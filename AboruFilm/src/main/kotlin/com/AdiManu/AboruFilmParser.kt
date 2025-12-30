package com.AdiManu

import com.fasterxml.jackson.annotation.JsonProperty

// Enum ini sekarang di luar class agar bisa diakses siapapun
enum class ResponseTypes(val value: Int) {
    Series(2),
    Movies(1);
    companion object {
        fun getResponseType(value: Int?) = entries.firstOrNull { it.value == value } ?: Movies
    }
}

data class LinkData(val id: Int, val type: Int, val season: Int?, val episode: Int?, val mediaId: Int?, val imdbId: String?)
data class LoadData(val id: Int, val box_type: Int?)

data class MovieDataProp(val data: MovieData? = null)
data class MovieData(
    val id: Int?, val title: String?, val poster: String?, val poster_org: String?, 
    val description: String?, val year: Int?, val imdb_id: String?, val imdb_rating: String?, 
    val trailer_url: String?
)

data class SeriesDataProp(val data: SeriesData? = null)
data class SeriesData(
    val id: Int?, val title: String?, val poster: String?, val poster_org: String?, 
    val description: String?, val imdb_id: String?, val imdb_rating: String?, val season: List<Int> = emptyList()
)

data class SeriesSeasonProp(
    @JsonProperty("data") val data: List<SeriesEpisode>? = emptyList()
)
data class SeriesEpisode(
    val id: Int?, val tid: Int?, val season: Int?, val episode: Int?, val title: String?, val synopsis: String?
)

data class LinkDataProp(val data: ParsedLinkData? = null)
data class ParsedLinkData(val list: List<LinkList> = emptyList())
data class LinkList(val path: String?, val quality: String?, val size: String?, val fid: Int?)

data class SubtitleDataProp(val data: PrivateSubtitleData? = null)
data class PrivateSubtitleData(val list: List<SubtitleList> = emptyList())
data class SubtitleList(val subtitles: List<Subtitles> = emptyList())
data class Subtitles(val filePath: String?, val lang: String?, val language: String?, val support_total: Int?)

data class ExternalResponse(val data: ExternalData? = null)
data class ExternalData(val link: String?, val file_list: List<ExternalFile>? = emptyList())
data class ExternalFile(val fid: Long?, val file_name: String?)

data class OsResult(val subtitles: List<OsSubtitles>? = emptyList())
data class OsSubtitles(val url: String?, val lang: String?)

data class WatchsomuchResponses(val movie: WatchsomuchMovies? = null)
data class WatchsomuchMovies(val torrents: List<WatchsomuchTorrents>? = emptyList())
data class WatchsomuchTorrents(val id: Int?, val season: Int?, val episode: Int?)
data class WatchsomuchSubResponses(val subtitles: List<WatchsomuchSubtitles>? = emptyList())
data class WatchsomuchSubtitles(val url: String?, val label: String?)
