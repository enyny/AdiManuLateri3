package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// ================= PrimeWire (PrimeSrc) =================

data class PrimeSrcServerList(
    val servers: List<PrimeSrcServer>,
)

data class PrimeSrcServer(
    val name: String,
    val key: String,
    @JsonProperty("file_size")
    val fileSize: String?,
    @JsonProperty("file_name")
    val fileName: String?,
)

// ================= RiveStream =================

data class RiveStreamSource(
    val data: List<String>
)

// ================= Wyzie Subtitles =================

data class WyZIESUB(
    val id: String,
    val url: String,
    val flagUrl: String,
    val format: String,
    val display: String,
    val language: String,
    val media: String,
    val isHearingImpaired: Boolean,
)

// ================= SubtitleAPI =================

data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String? = null,
    val lang: String,
    val m: String? = null,
    val g: String? = null,
)
