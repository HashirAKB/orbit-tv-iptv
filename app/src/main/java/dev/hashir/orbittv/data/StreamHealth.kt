package dev.hashir.orbittv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tracks which stream URLs are actually alive.
 *
 * A large share of the iptv-org list is dead at any moment, so the app learns
 * two ways: instantly, whenever playback fails, and in the background, by
 * probing the channels the user is most likely to open next. Nothing is
 * probed speculatively across the whole 10k-entry list -- that would be slow
 * and rude to the origins.
 */
class StreamHealth(private val context: Context) {

    /** url -> last check. alive == false hides the channel while it is fresh. */
    private val results = HashMap<String, Entry>()

    private val _version = MutableStateFlow(0)

    /** Bumps whenever results change, so Compose can recompose filtered lists. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val file: File get() = File(context.filesDir, "stream-health.tsv")

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(true)
        .build()

    data class Entry(val alive: Boolean, val checkedAt: Long)

    suspend fun restore() {
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext
            runCatching {
                synchronized(results) {
                    results.clear()
                    file.forEachLine { line ->
                        val parts = line.split('\t')
                        if (parts.size == 3) {
                            results[parts[0]] = Entry(parts[1] == "1", parts[2].toLongOrNull() ?: 0L)
                        }
                    }
                }
            }
        }
        _version.value = _version.value + 1
    }

    private suspend fun persist() {
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = synchronized(results) { results.entries.map { it.key to it.value } }
                file.writeText(
                    snapshot.joinToString("\n") { (url, e) ->
                        url + "\t" + (if (e.alive) "1" else "0") + "\t" + e.checkedAt
                    }
                )
            }
        }
    }

    /** True when this URL is known-dead and that knowledge is still recent. */
    fun isKnownDead(url: String): Boolean {
        val e = synchronized(results) { results[url] } ?: return false
        return !e.alive && System.currentTimeMillis() - e.checkedAt < DEAD_TTL_MS
    }

    /** A channel counts as offline only when every one of its sources is dead. */
    fun isChannelOffline(channel: Channel): Boolean =
        channel.streams.isNotEmpty() && channel.streams.all { isKnownDead(it.url) }

    fun record(url: String, alive: Boolean) {
        synchronized(results) {
            if (results.size > MAX_ENTRIES) {
                val cutoff = System.currentTimeMillis() - DEAD_TTL_MS
                val stale = results.entries.filter { it.value.checkedAt < cutoff }.map { it.key }
                stale.forEach { results.remove(it) }
            }
            results[url] = Entry(alive, System.currentTimeMillis())
        }
        _version.value = _version.value + 1
    }

    /** Marks a stream dead after a real playback failure. Strongest signal there is. */
    suspend fun reportPlaybackFailure(url: String) {
        record(url, false)
        persist()
    }

    suspend fun reportPlaybackSuccess(url: String) {
        record(url, true)
        persist()
    }

    /**
     * Probes up to [limit] not-recently-checked sources from [channels],
     * one source per channel, with a small amount of concurrency.
     */
    suspend fun probe(channels: List<Channel>, limit: Int = 120) {
        val now = System.currentTimeMillis()
        val targets = channels.asSequence()
            .mapNotNull { it.streams.firstOrNull() }
            .filter { source ->
                val e = synchronized(results) { results[source.url] }
                e == null || now - e.checkedAt > RECHECK_MS
            }
            .take(limit)
            .toList()

        if (targets.isEmpty()) return

        coroutineScope {
            val gate = Semaphore(8)
            targets.map { source ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val alive = runCatching { ping(source) }.getOrDefault(false)
                        record(source.url, alive)
                    }
                }
            }.forEach { it.await() }
        }

        persist()
    }

    private fun ping(source: StreamSource): Boolean {
        val builder = Request.Builder()
            .url(source.url)
            .header("User-Agent", source.userAgent ?: CatalogRepository.APP_UA)
            .header("Range", "bytes=0-1024")
        source.referrer?.let { builder.header("Referer", it) }
        return runCatching {
            http.newCall(builder.get().build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private companion object {
        val DEAD_TTL_MS: Long = TimeUnit.DAYS.toMillis(5)
        val RECHECK_MS: Long = TimeUnit.HOURS.toMillis(12)
        const val MAX_ENTRIES = 4000
    }
}
