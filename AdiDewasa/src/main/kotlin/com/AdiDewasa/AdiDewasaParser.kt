package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

data class HomeResponse(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null,
    @JsonProperty("success") val success: Boolean? = null
)

data class MediaItem(
    @JsonProperty("is_adult") val isAdult: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)
