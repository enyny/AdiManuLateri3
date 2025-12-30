package com.AdiManu

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// --- Model Detail & Meta ---
data class Meta(
    val id: String?,
    val imdb_id: String?,
    val cast: List<String>?,
    val genre: List<String>?,
    val background: String?
)

data class ResponseData(val meta: Meta?)

// --- Model API Utama ---
data class MovieDataProp(val data: MovieData? = null)
data class MovieData(
    val id: Int?,
    val title: String?,
    val poster: String?,
    val description: String?,
    val year: Int?,
    val imdb_id: String?,
    val imdb_rating: String?,
    val trailer_url: String?,
    val recommend: List<AboruFilm.SearchData> = emptyList()
)

data class SeriesDataProp(val data: SeriesData? = null)
data class SeriesData(
    val id: Int?,
    val title: String?,
    val poster: String?,
    val description: String?,
    val imdb_id: String?,
    val imdb_rating: String?,
    val season: List<Int> = emptyList(),
    val cats: String?
)

data class SeriesSeasonProp(val data: List<SeriesEpisode>? = null)
data class SeriesEpisode(
    val id: Int?,
    val tid: Int?,
    val season: Int?,
    val episode: Int?,
    val title: String?,
    val synopsis: String?,
    val thumbs_original: String?,
    val released_timestamp: Long?,
    val runtime: Int?,
    val imdb_rating: String?
)

// --- Model Link & Subtitle ---
data class LinkDataProp(val data: ParsedLinkData? = null)
data class ParsedLinkData(val list: List<LinkList> = emptyList())
data class LinkList(
    val path: String?,
    val quality: String?,
    val size: String?,
    val fid: Int?
)

data class SubtitleDataProp(val data: PrivateSubtitleData? = null)
data class PrivateSubtitleData(val list: List<SubtitleList> = emptyList())
data class SubtitleList(val subtitles: List<Subtitles> = emptyList())
data class Subtitles(
    val filePath: String?,
    val lang: String?,
    val language: String?,
    val support_total: Int?
)

// --- Model External (Febbox/Watchsomuch/OpenSubs) ---
data class ExternalResponse(val data: ExternalData? = null)
data class ExternalData(
    val link: String?,
    val file_list: List<ExternalFile>? = emptyList()
)
data class ExternalFile(val fid: Long?, val file_name: String?)

data class OsResult(val subtitles: List<OsSubtitles>? = emptyList())
data class OsSubtitles(val url: String?, val lang: String?)

data class WatchsomuchResponses(val movie: WatchsomuchMovies? = null)
data class WatchsomuchMovies(val torrents: List<WatchsomuchTorrents>? = emptyList())
data class WatchsomuchTorrents(val id: Int?, val season: Int?, val episode: Int?)
data class WatchsomuchSubResponses(val subtitles: List<WatchsomuchSubtitles>? = emptyList())
data class WatchsomuchSubtitles(val url: String?, val label: String?)
