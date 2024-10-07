package com.github.topi314.lavasrc.protocol

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val spotify: SpotifyConfig? = null,
    val appleMusic: AppleMusicConfig? = null,
    val deezer: DeezerConfig? = null,
    val yandexMusic: YandexMusicConfig? = null,
    val vkMusic: VkMusicConfig? = null,
    val tidal: TidalConfig? = null
)

@Serializable
data class SpotifyConfig(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val spDc: String? = null,
)

@Serializable
data class AppleMusicConfig(
    val mediaAPIToken: String? = null,
)

@Serializable
data class DeezerConfig(
    val arl: String? = null,
    val formats: List<DeezerTrackFormat>? = null,
)

@Suppress("unused")
@Serializable
enum class DeezerTrackFormat {
    FLAC,
    MP3_320,
    MP3_256,
    MP3_128,
    MP3_64,
    AAC_64
}

@Serializable
data class YandexMusicConfig(
    val accessToken: String? = null,
)

@Serializable
data class VkMusicConfig(
    val userToken: String? = null,
)

@Serializable
data class TidalConfig(
    val tidalToken: String? = null,
    val countryCode: String? = null,
    val tracksSearchLimit: Int? = null,
    val playlistTracksLoadLimit: Int? = null,
    val artistTopTracksLoadLimit: Int? = null,
)