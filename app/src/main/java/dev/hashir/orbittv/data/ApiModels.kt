package dev.hashir.orbittv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the iptv-org public API (https://iptv-org.github.io/api/).
 * Everything is tolerant of missing/unknown fields — the upstream data set
 * changes shape from time to time and the app must not hard-fail on that.
 */

@Serializable
data class ApiChannel(
    val id: String,
    val name: String = "",
    @SerialName("alt_names") val altNames: List<String> = emptyList(),
    val network: String? = null,
    val owners: List<String> = emptyList(),
    val country: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    val launched: String? = null,
    val closed: String? = null,
    @SerialName("replaced_by") val replacedBy: String? = null,
    val website: String? = null,
)

@Serializable
data class ApiFeed(
    val channel: String = "",
    val id: String = "",
    val name: String = "",
    @SerialName("is_main") val isMain: Boolean = false,
    val languages: List<String> = emptyList(),
    val format: String? = null,
)

@Serializable
data class ApiLogo(
    val channel: String = "",
    val feed: String? = null,
    @SerialName("in_use") val inUse: Boolean = false,
    val tags: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val format: String? = null,
    val url: String = "",
)

@Serializable
data class ApiStream(
    val channel: String? = null,
    val feed: String? = null,
    val title: String = "",
    val url: String = "",
    val referrer: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    val quality: String? = null,
    val label: String? = null,
)

@Serializable
data class ApiCategory(
    val id: String = "",
    val name: String = "",
    val description: String = "",
)

@Serializable
data class ApiCountry(
    val name: String = "",
    val code: String = "",
    val languages: List<String> = emptyList(),
    val flag: String = "",
)

@Serializable
data class ApiLanguage(
    val name: String = "",
    val code: String = "",
)
