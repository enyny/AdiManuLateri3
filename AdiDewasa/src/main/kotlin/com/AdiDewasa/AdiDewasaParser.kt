package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// Respon Search dari API Dramafull
data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)

// Item media dari Dramafull
data class MediaItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

// Respon Halaman Utama (Filter) Dramafull
data class HomeResponse(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null
)

// Data class untuk komunikasi antar fungsi (Wajib untuk LoadLinks)
data class LinkData(
    val url: String,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val type: String? = null
)
