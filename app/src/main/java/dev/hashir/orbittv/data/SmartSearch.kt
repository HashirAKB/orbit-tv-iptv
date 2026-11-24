package dev.hashir.orbittv.data

import dev.hashir.orbittv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** What a plain-English request ("Hindi movie channels") boils down to. */
@Serializable
data class ChannelQuery(
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    val title: String = "",
) {
    val isEmpty: Boolean get() = countries.isEmpty() && languages.isEmpty() && categories.isEmpty() && names.isEmpty()
}

data class SmartResult(
    val query: ChannelQuery,
    val channels: List<Channel>,
    /** Set when the exact combination had nothing and filters were loosened. */
    val relaxedNote: String? = null,
)

/**
 * Natural-language search: a Groq-hosted LLM turns the request into filters,
 * then the filters run locally against the in-memory catalogue. Only the
 * request text and the category ids leave the device.
 */
class SmartSearch(
    private val apiKey: String = BuildConfig.GROQ_API_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    val isAvailable: Boolean get() = apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun parse(text: String, categoryIds: List<String>): ChannelQuery = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", MODEL)
            put("temperature", 0)
            put("reasoning_effort", "low")
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt(categoryIds)) })
                add(buildJsonObject { put("role", "user"); put("content", text) })
            })
        }.toString()

        val request = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Groq HTTP ${resp.code}: ${raw.take(200)}")
            val content = json.parseToJsonElement(raw).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            json.decodeFromString(ChannelQuery.serializer(), content).normalised().withLiteralCategories(text, categoryIds)
        }
    }

    /** Safety net: a category named outright in the request ("... news") is always applied. */
    private fun ChannelQuery.withLiteralCategories(text: String, categoryIds: List<String>): ChannelQuery {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).toSet()
        val literal = categoryIds.filter { id -> id in words || "${id}s" in words || id.removeSuffix("s") in words }
        val extra = literal.filter { it !in categories && it != "general" }
        return if (extra.isEmpty()) this else copy(categories = categories + extra)
    }

    private fun ChannelQuery.normalised() = copy(
        countries = countries.map { it.trim().uppercase() }.filter { it.length == 2 },
        languages = languages.map { it.trim().lowercase() }.filter { it.length == 3 },
        categories = categories.map { it.trim().lowercase() },
        names = names.map { it.trim() }.filter { it.length >= 2 },
    )

    companion object {
        const val MODEL = "openai/gpt-oss-120b"
        private const val URL = "https://api.groq.com/openai/v1/chat/completions"

        private fun systemPrompt(categoryIds: List<String>) = """
You turn a TV viewer's request (often transcribed speech, possibly misheard) into search filters for a live-TV channel catalogue.
Reply with one JSON object only:
{"countries": [ISO 3166-1 alpha-2, uppercase], "languages": [ISO 639-3, lowercase], "categories": [ids from the list], "names": [channel or brand names the viewer named], "title": "short heading for the results"}
Rules:
- A language ("Hindi", "in Spanish") goes in languages. A country or nationality ("Indian", "from Pakistan") goes in countries. Only fill what was asked.
- Every genre or topic word (news, sports, movies, music, kids, comedy, documentary, religious, ...) MUST appear in categories, even when a country or language is also given. "Pakistani news" -> countries ["PK"], categories ["news"].
- Map genres to the closest category ids: films/cinema -> movies, cartoons -> animation or kids, football/cricket -> sports, songs -> music, drama/serials -> series or entertainment.
- names only for specific channels/brands (e.g. "BBC", "Al Jazeera", "Star"), never generic words.
- Fix obvious transcription slips (e.g. "hindu movies" -> Hindi, "malayalum" -> Malayalam).
- Use empty arrays for anything not specified.
Category ids: ${categoryIds.joinToString(" ")}
""".trim()

        /**
         * Runs [q] against [pool]. If the full combination finds nothing, drops
         * the least essential filter (categories, then names) and says so.
         */
        fun apply(q: ChannelQuery, pool: List<Channel>, limit: Int = 400): SmartResult {
            fun run(countries: Boolean, languages: Boolean, categories: Boolean, names: Boolean): List<Channel> {
                val c = if (countries) q.countries.toSet() else emptySet()
                val l = if (languages) q.languages.toSet() else emptySet()
                val k = if (categories) q.categories.toSet() else emptySet()
                val n = if (names) q.names.map { it.lowercase() } else emptyList()
                if (c.isEmpty() && l.isEmpty() && k.isEmpty() && n.isEmpty()) return emptyList()
                return pool.asSequence()
                    .filter { ch -> c.isEmpty() || ch.countryCode in c }
                    .filter { ch -> l.isEmpty() || ch.languages.any { it in l } }
                    .filter { ch -> k.isEmpty() || ch.categories.any { it in k } }
                    .filter { ch -> n.isEmpty() || n.any { ch.search.contains(it) } }
                    .sortedWith(
                        compareBy<Channel>(
                            // Named channels whose name starts with the brand first.
                            { ch -> if (n.any { ch.name.lowercase().startsWith(it) }) 0 else 1 },
                            { ch -> -(ch.streams.maxOfOrNull { it.heightRank } ?: 0) },
                            { it.name },
                        ),
                    )
                    .take(limit)
                    .toList()
            }

            val exact = run(true, true, true, true)
            if (exact.isNotEmpty() || q.isEmpty) return SmartResult(q, exact)

            if (q.categories.isNotEmpty()) {
                val loose = run(true, true, false, true)
                if (loose.isNotEmpty()) return SmartResult(q, loose, "No ${q.categories.joinToString("/")} channels matched, so showing everything else that fits.")
            }
            if (q.names.isNotEmpty()) {
                val loose = run(true, true, true, false)
                if (loose.isNotEmpty()) return SmartResult(q, loose, "Couldn't find ${q.names.joinToString()}, so showing similar channels.")
            }
            return SmartResult(q, emptyList())
        }
    }
}
