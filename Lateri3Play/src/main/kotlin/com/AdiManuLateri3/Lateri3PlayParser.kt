package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// ==================== TMDB & MAIN STRUCTURES ====================

data class LinkData(
    val id: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val type: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val year: Int? = null,
    val orgTitle: String? = null,
    val epsTitle: String? = null,
    val date: String? = null,
    val isAnime: Boolean = false,
    val isAsian: Boolean = false,
    val isBollywood: Boolean = false,
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
    @JsonProperty("original_language") val originalLanguage: String? = null,
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
    @JsonProperty("tvdb_id") val tvdb_id: Int? = null,
)

data class LastEpisodeToAir(
    @JsonProperty("season_number") val season_number: Int? = null,
)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String
)

// ==================== DOMAINS & EXTRACTOR PARSERS ====================

data class DomainsParser(
    val moviesdrive: String,
    @JsonProperty("HDHUB4u") val hdhub4u: String,
    @JsonProperty("4khdhub") val n4khdhub: String,
    @JsonProperty("MultiMovies") val multiMovies: String,
    val bollyflix: String,
    @JsonProperty("UHDMovies") val uhdmovies: String,
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

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

// Data class untuk dekripsi Idlix
data class AesData(
    @JsonProperty("m") val m: String,
)

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

// ==================== NEW SOURCES DATA ====================

data class VixsrcSource(val name: String, val url: String, val referer: String)

data class VidFastSources(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = null,
) {
    data class Tracks(@JsonProperty("file") val file: String? = null, @JsonProperty("label") val label: String? = null)
}

data class VidFastServers(@JsonProperty("name") val name: String? = null, @JsonProperty("description") val description: String? = null, @JsonProperty("data") val data: String? = null)

data class VidlinkSources(@JsonProperty("stream") val stream: Stream? = null) {
    data class Stream(@JsonProperty("playlist") val playlist: String? = null)
}

data class MappleSubtitle(@JsonProperty("display") val display: String? = null, @JsonProperty("url") val url: String? = null)

data class MappleSources(@JsonProperty("data") val data: Data? = null) {
    data class Data(@JsonProperty("stream_url") val stream_url: String? = null)
}

data class VidsrcccServer(@JsonProperty("name") val name: String? = null, @JsonProperty("hash") val hash: String? = null)
data class VidsrcccResponse(@JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf())
data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources? = null)
data class VidsrcccSources(@JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>? = arrayListOf(), @JsonProperty("source") val source: String? = null)
data class VidsrcccSubtitles(@JsonProperty("label") val label: String? = null, @JsonProperty("file") val file: String? = null)
data class UpcloudSources(@JsonProperty("file") val file: String? = null)
data class UpcloudResult(@JsonProperty("sources") val sources: ArrayList<UpcloudSources>? = arrayListOf())

data class Player4uLinkData(val name: String, val url: String)

data class AdiDewasaSearchResponse(@JsonProperty("data") val data: ArrayList<AdiDewasaItem>? = arrayListOf(), @JsonProperty("success") val success: Boolean? = null)
data class AdiDewasaItem(@JsonProperty("name") val name: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("slug") val slug: String? = null, @JsonProperty("image") val image: String? = null, @JsonProperty("year") val year: String? = null)

data class AdimovieboxSearch(val data: AdimovieboxData?)
data class AdimovieboxData(val items: List<AdimovieboxItem>?)
data class AdimovieboxItem(val subjectId: String?, val title: String?, val releaseDate: String?, val detailPath: String?)
data class AdimovieboxStreams(val data: AdimovieboxStreamData?)
data class AdimovieboxStreamData(val streams: List<AdimovieboxStreamItem>?)
data class AdimovieboxStreamItem(val id: String?, val format: String?, val url: String?, val resolutions: String?)
data class AdimovieboxCaptions(val data: AdimovieboxCaptionData?)
data class AdimovieboxCaptionData(val captions: List<AdimovieboxCaptionItem>?)
data class AdimovieboxCaptionItem(val lanName: String?, val url: String?)

data class KisskhMedia(@JsonProperty("id") val id: Int?, @JsonProperty("title") val title: String?)
data class KisskhDetail(@JsonProperty("episodes") val episodes: ArrayList<KisskhEpisode>?)
data class KisskhEpisode(@JsonProperty("id") val id: Int?, @JsonProperty("number") val number: Double?)
data class KisskhKey(@JsonProperty("key") val key: String?)
data class KisskhSources(@JsonProperty("Video") val video: String?, @JsonProperty("ThirdParty") val thirdParty: String?)
data class KisskhSubtitle(@JsonProperty("src") val src: String?, @JsonProperty("label") val label: String?)
