package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// Data class untuk Search internal Dramafull (Dipakai di loadLinks)
data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)

data class MediaItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null
)

// Data class standar TMDB Provider Cloudstream
// Ini wajib ada agar fungsi loadLinks bisa menerima data dari TmdbProvider
data class LinkData(
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val type: String? = null
)
