package com.AdiManu
import com.fasterxml.jackson.annotation.JsonProperty

data class Meta(val id: String?, val imdb_id: String?, val type: String?, val poster: String?, val name: String?, val description: String?, val cast: List<String>?, val genre: List<String>?, val year: String?)
data class ResponseData(val meta: Meta?)
data class ExternalSourcesWrapper(@JsonProperty("sources") val sources: List<ExternalSources>? = null)
data class ExternalSources(val source: String?, val file: String?, val label: String?, val type: String?, val size: String?)

// Tambahan untuk detail API
data class MovieDataProp(val data: MovieData? = null)
data class MovieData(val id: Int?, val title: String?, val poster: String?, val description: String?, val year: Int?, val imdb_id: String?, val imdb_rating: String?)
data class SeriesDataProp(val data: SeriesData? = null)
data class SeriesData(val id: Int?, val title: String?, val poster: String?, val description: String?, val imdb_id: String?, val season: List<Int> = emptyList())
data class SeriesSeasonProp(val data: List<SeriesEpisode>? = null)
data class SeriesEpisode(val id: Int?, val tid: Int?, val season: Int?, val episode: Int?, val title: String?)
