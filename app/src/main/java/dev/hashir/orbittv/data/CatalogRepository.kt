package dev.hashir.orbittv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads the iptv-org data set, joins it into a browsable catalogue and
 * keeps a merged copy on disk so later cold starts are near-instant.
 */
class CatalogRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading("Starting up", 0f))
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val cacheFile: File get() = File(context.filesDir, CACHE_NAME)

    /**
     * Ensures a catalogue is available. Uses the disk cache when it is fresh
     * unless [force] is set, in which case the network copy always wins.
     */
    suspend fun load(force: Boolean = false) {
        mutex.withLock {
            if (!force && _state.value is CatalogState.Ready) return
            withContext(Dispatchers.IO) { loadInner(force) }
        }
    }

    private fun loadInner(force: Boolean) {
        val cached: Catalog? = if (cacheFile.exists()) runCatching { readCache() }.getOrNull() else null
        val cacheFresh = cached != null && !cached.isEmpty &&
            (System.currentTimeMillis() - cacheFile.lastModified()) < CACHE_TTL_MS

        if (!force && cacheFresh) {
            _state.value = CatalogState.Ready(cached!!, fromCache = true)
            return
        }

        try {
            _state.value = CatalogState.Loading("Contacting iptv-org", 0.05f)
            val catalog = downloadAndMerge()
            runCatching { writeCache(catalog) }
            _state.value = CatalogState.Ready(catalog, fromCache = false)
        } catch (t: Throwable) {
            // Network failed — a stale cache still beats an empty screen.
            if (cached != null && !cached.isEmpty) {
                _state.value = CatalogState.Ready(cached, fromCache = true)
            } else {
                _state.value = CatalogState.Failed(t.message ?: "Could not reach the channel list")
            }
        }
    }

    private fun downloadAndMerge(): Catalog {
        _state.value = CatalogState.Loading("Downloading channels", 0.10f)
        val channels: List<ApiChannel> = fetchList(BASE + "channels.json")

        _state.value = CatalogState.Loading("Downloading streams", 0.35f)
        val streams: List<ApiStream> = fetchList(BASE + "streams.json")

        _state.value = CatalogState.Loading("Downloading logos", 0.55f)
        val logos: List<ApiLogo> = runCatching { fetchList<ApiLogo>(BASE + "logos.json") }.getOrDefault(emptyList())

        _state.value = CatalogState.Loading("Downloading feeds", 0.70f)
        val feeds: List<ApiFeed> = runCatching { fetchList<ApiFeed>(BASE + "feeds.json") }.getOrDefault(emptyList())

        _state.value = CatalogState.Loading("Downloading regions", 0.80f)
        val countries: List<ApiCountry> = runCatching { fetchList<ApiCountry>(BASE + "countries.json") }.getOrDefault(emptyList())
        val categories: List<ApiCategory> = runCatching { fetchList<ApiCategory>(BASE + "categories.json") }.getOrDefault(emptyList())

        _state.value = CatalogState.Loading("Organising ${channels.size} channels", 0.90f)
        return merge(channels, streams, logos, feeds, countries, categories)
    }

    private inline fun <reified T> fetchList(url: String): List<T> {
        val request = Request.Builder().url(url).header("User-Agent", APP_UA).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val body = response.body ?: error("Empty body for $url")
            return json.decodeFromString<List<T>>(body.string())
        }
    }

    private fun merge(
        apiChannels: List<ApiChannel>,
        apiStreams: List<ApiStream>,
        apiLogos: List<ApiLogo>,
        apiFeeds: List<ApiFeed>,
        apiCountries: List<ApiCountry>,
        apiCategories: List<ApiCategory>,
    ): Catalog {
        val countryByCode = apiCountries.associateBy { it.code }
        val categoryNameById = apiCategories.associate { it.id to it.name }

        // Streams, grouped by channel. Sources without a channel id carry no
        // metadata or artwork, so they are skipped rather than shown bare.
        val streamsByChannel = HashMap<String, MutableList<StreamSource>>()
        for (s in apiStreams) {
            val cid = s.channel ?: continue
            if (s.url.isBlank()) continue
            streamsByChannel.getOrPut(cid) { mutableListOf() }.add(
                StreamSource(
                    url = s.url,
                    quality = s.quality,
                    referrer = s.referrer,
                    userAgent = s.userAgent,
                    label = s.label,
                    feed = s.feed,
                )
            )
        }

        // Best logo per channel: in-use first, then a format Coil renders well,
        // then the largest available.
        val logoByChannel = HashMap<String, ApiLogo>()
        for (l in apiLogos) {
            if (l.url.isBlank() || l.channel.isBlank()) continue
            val current = logoByChannel[l.channel]
            if (current == null || logoScore(l) > logoScore(current)) logoByChannel[l.channel] = l
        }

        val languagesByChannel = HashMap<String, MutableSet<String>>()
        for (f in apiFeeds) {
            if (f.channel.isBlank()) continue
            languagesByChannel.getOrPut(f.channel) { linkedSetOf() }.addAll(f.languages)
        }

        val result = ArrayList<Channel>(streamsByChannel.size)
        for (c in apiChannels) {
            if (c.closed != null) continue                      // off the air for good
            val sources = streamsByChannel[c.id] ?: continue     // nothing to play
            val country = countryByCode[c.country]
            result += Channel(
                id = c.id,
                name = c.name.ifBlank { c.id },
                altNames = c.altNames,
                countryCode = c.country,
                countryName = country?.name ?: c.country,
                flag = country?.flag ?: "",
                categories = c.categories,
                categoryNames = c.categories.map { categoryNameById[it] ?: it.replaceFirstChar(Char::uppercase) },
                languages = languagesByChannel[c.id]?.toList().orEmpty(),
                logo = logoByChannel[c.id]?.url,
                isNsfw = c.isNsfw,
                streams = sources.sortedByDescending { it.heightRank },
            )
        }

        result.sortBy { it.name.lowercase() }

        val countryCounts = result.groupingBy { it.countryCode }.eachCount()
        val countryInfos = countryCounts.entries
            .filter { it.key.isNotBlank() }
            .map { (code, count) ->
                val meta = countryByCode[code]
                CountryInfo(code, meta?.name ?: code, meta?.flag ?: "", count)
            }
            .sortedByDescending { it.count }

        val categoryCounts = HashMap<String, Int>()
        result.forEach { ch -> ch.categories.forEach { categoryCounts[it] = (categoryCounts[it] ?: 0) + 1 } }
        val categoryInfos = categoryCounts.entries
            .map { (id, count) -> CategoryInfo(id, categoryNameById[id] ?: id.replaceFirstChar(Char::uppercase), count) }
            .sortedByDescending { it.count }

        return Catalog(result, countryInfos, categoryInfos)
    }

    private fun logoScore(l: ApiLogo): Int {
        val formatRank = when (l.format?.uppercase()) {
            "PNG" -> 50
            "WEBP" -> 45
            "JPEG", "JPG" -> 40
            "SVG" -> 30
            "AVIF" -> 20
            "GIF" -> 10
            else -> 25
        }
        return (if (l.inUse) 1000 else 0) + formatRank + (l.width / 100).coerceAtMost(20)
    }

    // ---- disk cache -------------------------------------------------------

    private fun writeCache(catalog: Catalog) {
        val snapshot = CachedCatalog(catalog.channels, catalog.countries, catalog.categories)
        cacheFile.writeText(json.encodeToString(CachedCatalog.serializer(), snapshot))
    }

    private fun readCache(): Catalog {
        val snapshot = json.decodeFromString(CachedCatalog.serializer(), cacheFile.readText())
        return Catalog(snapshot.channels, snapshot.countries, snapshot.categories)
    }

    fun cacheAgeMillis(): Long =
        if (cacheFile.exists()) System.currentTimeMillis() - cacheFile.lastModified() else -1L

    companion object {
        private const val BASE = "https://iptv-org.github.io/api/"
        private const val CACHE_NAME = "catalog-v1.json"
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)
        const val APP_UA = "OrbitTV/1.0 (Android)"
    }
}

@kotlinx.serialization.Serializable
private data class CachedCatalog(
    val channels: List<Channel>,
    val countries: List<CountryInfo>,
    val categories: List<CategoryInfo>,
)
