package com.AdiManu

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// --- Model Detail & Meta ---
data class Meta(
    val id: String? = null,
    val imdb_id: String? = null,
    val cast: List<String>? = emptyList(),
    val genre: List<String>? = emptyList(),
    val background: String? = null,
    val description: String? = null
)

data class ResponseData(val meta: Meta? = null)

// --- Model API Utama (Movies & Series) ---
data class MovieDataProp(val data: MovieData? = null)
data class MovieData(
    val id: Int? = null,
    val title: String? = null,
    val poster: String? = null,
    val poster_org: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val imdb_id: String? = null,
    val imdb_rating: String? = null,
    val trailer_url: String? = null,
    val cats: String? = null,
    val recommend: List<AboruFilm.SearchData> = emptyList()
)

data class SeriesDataProp(val data: SeriesData? = null)
data class SeriesData(
    val id: Int? = null,
    val title: String? = null,
    val poster: String? = null,
    val poster_org: String? = null,
    val description: String? = null,
    val imdb_id: String? = null,
    val imdb_rating: String? = null,
    val year: Int? = null,
    val cats: String? = null,
    val season: List<Int> = emptyList()
)

data class SeriesSeasonProp(
    @JsonProperty("data") val data: List<SeriesEpisode>? = emptyList()
)
data class SeriesEpisode(
    val id: Int? = null,
    val tid: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val thumbs_original: String? = null,
    val runtime: Int? = null,
    val imdb_rating: String? = null
)

// --- Model Link Internal ---
data class LinkDataProp(
    @SerializedName("data") val data: ParsedLinkData? = null
)
data class ParsedLinkData(
    @JsonProperty("list") val list: List<LinkList> = emptyList()
)
data class LinkList(
    val path: String? = null,
    val quality: String? = null,
    val size: String? = null,
    val fid: Int? = null
)

// --- Model Subtitle Internal ---
data class SubtitleDataProp(val data: PrivateSubtitleData? = null)
data class PrivateSubtitleData(val list: List<SubtitleList> = emptyList())
data class SubtitleList(val subtitles: List<Subtitles> = emptyList())
data class Subtitles(
    val filePath: String? = null,
    val lang: String? = null,
    val language: String? = null,
    val support_total: Int? = null
)

// --- Model External (Febbox) ---
data class ExternalResponse(val data: ExternalData? = null)
data class ExternalData(
    val link: String? = null,
    val share_link: String? = null,
    val file_list: List<ExternalFile>? = emptyList()
)
data class ExternalFile(val fid: Long? = null, val file_name: String? = null)

// --- Model Third Party (OpenSubs & Watchsomuch) ---
data class OsResult(val subtitles: List<OsSubtitles>? = emptyList())
data class OsSubtitles(val url: String? = null, val lang: String? = null)

data class WatchsomuchResponses(val movie: WatchsomuchMovies? = null)
data class WatchsomuchMovies(val torrents: List<WatchsomuchTorrents>? = emptyList())
data class WatchsomuchTorrents(val id: Int? = null, val season: Int? = null, val episode: Int? = null)
data class WatchsomuchSubResponses(val subtitles: List<WatchsomuchSubtitles>? = emptyList())
data class WatchsomuchSubtitles(val url: String? = null, val label: String? = null)

data class ExternalSourcesWrapper(
    @JsonProperty("sources") val sources: List<ExternalSources>? = null
)
data class ExternalSources(
    val file: String? = null,
    val label: String? = null,
    val type: String? = null,
    val size: String? = null
)
