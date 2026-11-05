package dev.hashir.orbittv.data

import kotlinx.serialization.Serializable

/** A single playable source for a channel. */
@Serializable
data class StreamSource(
    val url: String,
    val quality: String? = null,
    val referrer: String? = null,
    val userAgent: String? = null,
    val label: String? = null,
    val feed: String? = null,
) {
    /** Rough vertical resolution, used to rank sources best-first. */
    val heightRank: Int
        get() = quality?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0

    val isGeoBlocked: Boolean get() = label?.contains("geo", ignoreCase = true) == true
}

/** A channel, already joined against streams, logos, countries and categories. */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val altNames: List<String> = emptyList(),
    val countryCode: String = "",
    val countryName: String = "",
    val flag: String = "",
    val categories: List<String> = emptyList(),
    val categoryNames: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val logo: String? = null,
    val isNsfw: Boolean = false,
    val streams: List<StreamSource> = emptyList(),
) {
    val bestQuality: String? get() = streams.maxByOrNull { it.heightRank }?.quality

    /** Lower-cased haystack used by search. Built once, at merge time. */
    val search: String = buildString {
        append(name.lowercase()).append(' ')
        altNames.forEach { append(it.lowercase()).append(' ') }
        append(countryName.lowercase()).append(' ')
        append(countryCode.lowercase()).append(' ')
        categoryNames.forEach { append(it.lowercase()).append(' ') }
    }
}

@Serializable
data class CategoryInfo(val id: String, val name: String, val count: Int)

@Serializable
data class CountryInfo(val code: String, val name: String, val flag: String, val count: Int)

/** The whole in-memory catalogue plus the indexes the UI browses by. */
data class Catalog(
    val channels: List<Channel> = emptyList(),
    val countries: List<CountryInfo> = emptyList(),
    val categories: List<CategoryInfo> = emptyList(),
) {
    val byId: Map<String, Channel> = channels.associateBy { it.id }

    val byCountry: Map<String, List<Channel>> = channels.groupBy { it.countryCode }

    val byCategory: Map<String, List<Channel>> = buildMap<String, MutableList<Channel>> {
        channels.forEach { ch ->
            ch.categories.forEach { cat -> getOrPut(cat) { mutableListOf() }.add(ch) }
        }
    }

    val isEmpty: Boolean get() = channels.isEmpty()
}

/** Progress reported while the catalogue is being fetched and merged. */
sealed interface CatalogState {
    data class Loading(val step: String, val fraction: Float) : CatalogState
    data class Ready(val catalog: Catalog, val fromCache: Boolean) : CatalogState
    data class Failed(val message: String) : CatalogState
}
