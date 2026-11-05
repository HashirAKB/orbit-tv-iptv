package dev.hashir.orbittv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.hashir.orbittv.data.CatalogRepository
import dev.hashir.orbittv.data.StreamHealth
import dev.hashir.orbittv.data.UserPrefs

class OrbitApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    /**
     * Channel logos are a mix of PNG, WebP and SVG, so the SVG decoder is
     * registered globally. Logos are small and reused constantly, which makes
     * a generous disk cache worthwhile.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("logo_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .respectCacheHeaders(false)
            .build()
}

/** Tiny hand-rolled service locator. Fewer moving parts than a DI framework. */
object Graph {
    lateinit var catalog: CatalogRepository
        private set
    lateinit var prefs: UserPrefs
        private set
    lateinit var health: StreamHealth
        private set

    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        val app = context.applicationContext
        catalog = CatalogRepository(app)
        prefs = UserPrefs(app)
        health = StreamHealth(app)
        initialised = true
    }
}
