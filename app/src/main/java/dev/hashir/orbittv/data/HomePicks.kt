package dev.hashir.orbittv.data

/**
 * Hand-picked rows that lead the Home screen on this setup: Malayalam
 * channels first, then National Geographic and similar factual channels.
 * Used by Home (first 20) and by the "See all" page (everything).
 */
object HomePicks {
    const val KIND = "pick"
    const val MALAYALAM = "malayalam"
    const val NATGEO = "natgeo"

    data class Pick(val key: String, val title: String)

    val all = listOf(
        Pick(MALAYALAM, "Malayalam"),
        Pick(NATGEO, "National Geographic & Discovery"),
    )

    fun channels(catalog: Catalog, key: String): List<Channel> = when (key) {
        MALAYALAM -> malayalam(catalog)
        NATGEO -> natGeo(catalog)
        else -> emptyList()
    }

    /** Well-known Malayalam channels, in roughly the order people look for them. */
    private val MALAYALAM_TOP = listOf(
        "asianet", "mazhavil manorama", "flowers", "zee keralam", "surya", "kairali", "amrita",
        "asianet news", "manorama news", "mathrubhumi news", "24 news", "reporter", "media one",
        "news18 kerala", "kairali news", "janam", "jaihind", "asianet movies", "kappa", "safari",
        "dd malayalam", "kite victers",
    )

    private fun malayalam(catalog: Catalog): List<Channel> {
        val pool = catalog.channels.filter { ch ->
            ch.streams.isNotEmpty() && ("mal" in ch.languages || ch.name.contains("malayalam", ignoreCase = true))
        }
        return pool.sortedWith(
            compareBy<Channel>(
                { ch -> rank(ch.name, MALAYALAM_TOP) },
                { ch -> -(ch.streams.maxOfOrNull { it.heightRank } ?: 0) },
                { it.name },
            ),
        )
    }

    private val ENGLISH_MARKETS = setOf("IN", "US", "GB", "AU", "CA", "AE", "SG")

    private val NATGEO_NAMES =listOf("national geographic", "nat geo", "natgeo")
    private val FACTUAL_NAMES = listOf(
        "discovery", "animal planet", "bbc earth", "history", "love nature", "smithsonian",
        "curiosity", "docubox", "travelxp", "nature", "wild", "earth",
    )

    private fun natGeo(catalog: Catalog): List<Channel> {
        val playable = catalog.channels.filter { it.streams.isNotEmpty() }
        fun english(ch: Channel) = ch.languages.isEmpty() || "eng" in ch.languages
        fun matches(ch: Channel, names: List<String>) = names.any { ch.name.lowercase().contains(it) }

        val natGeo = playable.filter { matches(it, NATGEO_NAMES) }
        val factual = playable.filter { !matches(it, NATGEO_NAMES) && matches(it, FACTUAL_NAMES) &&
            ("documentary" in it.categories || "science" in it.categories || "outdoor" in it.categories || "travel" in it.categories || matches(it, FACTUAL_NAMES.take(6))) }
        val docs = playable.filter { "documentary" in it.categories && english(it) }

        val byQuality = compareBy<Channel>(
            { if (english(it)) 0 else 1 },
            { if (it.countryCode in ENGLISH_MARKETS) 0 else 1 },
            { -(it.streams.maxOfOrNull { s -> s.heightRank } ?: 0) },
            { it.name },
        )
        // Nat Geo alone has a dozen regional feeds with the same name; keep the
        // best one per name (English market first) so the row has variety.
        fun bestPerName(list: List<Channel>) =
            list.sortedWith(byQuality).distinctBy { it.name.lowercase().trim() }
        return (bestPerName(natGeo) + bestPerName(factual) + bestPerName(docs))
            .distinctBy { it.id }
    }

    /** Position of the first list entry the name starts with or contains; unknown names go last. */
    private fun rank(name: String, order: List<String>): Int {
        val n = name.lowercase()
        val i = order.indexOfFirst { n == it || n.startsWith(it) }
        if (i >= 0) return i
        val j = order.indexOfFirst { n.contains(it) }
        return if (j >= 0) order.size + j else order.size * 2
    }
}
