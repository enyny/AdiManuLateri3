package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// ==================== TMDB & MAIN STRUCTURES ====================

data class LinkData(
    val id: Int? = null,
    val imdbId: String? = null,
    val type: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val year: Int? = null,
    val orgTitle: String? = null,
    val epsTitle: String? = null,
    val date: String? = null,
)

data class Data(
    val id: Int? = null,
    val type: String? = null,
)

data class Results(
    @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class Media(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
)

data class MediaDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("vote_average") val vote_average: Any? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
    @JsonProperty("keywords") val keywords: KeywordResults? = null,
    @JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
    @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
    @JsonProperty("videos") val videos: ResultsTrailer? = null,
    @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
    @JsonProperty("credits") val credits: Credits? = null,
    @JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
)

data class Genres(
    @JsonProperty("name") val name: String? = null,
)

data class KeywordResults(
    @JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
    @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
)

data class Keywords(
    @JsonProperty("name") val name: String? = null,
)

data class Seasons(
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("air_date") val airDate: String? = null,
)

data class MediaDetailEpisodes(
    @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
)

data class Episodes(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
)

data class Credits(
    @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
)

data class Cast(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null,
)

data class ResultsTrailer(
    @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
)

data class Trailers(
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class ResultsRecommendations(
    @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class ExternalIds(
    @JsonProperty("imdb_id") val imdb_id: String? = null,
)

data class LastEpisodeToAir(
    @JsonProperty("season_number") val season_number: Int? = null,
)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String
)

// ==================== CONFIGURATION & DOMAINS ====================

data class DomainsParser(
    val moviesdrive: String,
    @JsonProperty("HDHUB4u")
    val hdhub4u: String,
    @JsonProperty("4khdhub")
    val n4khdhub: String,
    @JsonProperty("MultiMovies")
    val multiMovies: String,
    val bollyflix: String,
    @JsonProperty("UHDMovies")
    val uhdmovies: String,
    val moviesmod: String,
    val topMovies: String,
    val hdmovie2: String,
    val vegamovies: String,
    val rogmovies: String,
    val luxmovies: String,
    val xprime: String,
    val extramovies: String,
    val dramadrip: String,
    val toonstream: String,
)

// ==================== COMMON RESPONSES ====================

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

// ==================== SUBTITLES ====================

data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)

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

// ==================== PROVIDER SPECIFIC PARSERS ====================

// --- RidoMovies ---
data class RidoSearch(
    @JsonProperty("data") var data: RidoData? = null,
)

data class RidoData(
    @JsonProperty("url") var url: String? = null,
    @JsonProperty("items") var items: ArrayList<RidoItems>? = arrayListOf(),
)

data class RidoItems(
    @JsonProperty("slug") var slug: String? = null,
    @JsonProperty("contentable") var contentable: RidoContentable? = null,
)

data class RidoContentable(
    @JsonProperty("imdbId") var imdbId: String? = null,
    @JsonProperty("tmdbId") var tmdbId: Int? = null,
)

data class RidoResponses(
    @JsonProperty("data") var data: ArrayList<RidoDataUrl>? = arrayListOf(),
)

data class RidoDataUrl(
    @JsonProperty("url") var url: String? = null,
)
