package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty

object TmdbConfig {

    data class Results(
        @JsonProperty("results") val results: List<Media> = emptyList(),
        @JsonProperty("page") val page: Int? = null
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null, // Untuk TV Series
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null, // Untuk TV Series
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: List<Genre>? = null,
        @JsonProperty("seasons") val seasons: List<Season>? = null,
        @JsonProperty("external_ids") val externalIds: ExternalIds? = null,
        @JsonProperty("videos") val videos: VideoContainer? = null
    )

    data class Genre(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null
    )

    data class Season(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null
    )

    data class SeasonDetail(
        @JsonProperty("episodes") val episodes: List<Episode>? = null
    )

    data class Episode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("tvdb_id") val tvdbId: Int? = null
    )

    data class VideoContainer(
        @JsonProperty("results") val results: List<VideoResult>? = null
    )

    data class VideoResult(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,
        @JsonProperty("type") val type: String? = null
    )
}
