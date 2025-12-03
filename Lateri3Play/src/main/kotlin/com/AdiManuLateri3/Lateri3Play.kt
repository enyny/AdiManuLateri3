package com.AdiManuLateri3

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeSubtitleAPI
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWyZIESUBAPI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

open class Lateri3Play(val sharedPref: SharedPreferences) : TmdbProvider() {
    override var name = "Lateri3Play"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Bahasa default
    val langCode = sharedPref.getString("tmdb_language_code", "en-US")

    companion object {
        private const val TMDB_API_URL = "https://api.themoviedb.org/3"
        private const val API_KEY = "1cfadd9dbfc534abf6de40e1e7eaf4c7"

        fun getApiBase(): String = TMDB_API_URL

        fun getDate(): TmdbDate {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            val today = formatter.format(calendar.time)
            
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            val nextWeek = formatter.format(calendar.time)
            
            calendar.time = Date()
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
            val lastWeekStart = formatter.format(calendar.time)
            
            return TmdbDate(today, nextWeek, lastWeekStart)
        }
    }

    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$API_KEY&region=US" to "Trending",
        "/trending/movie/week?api_key=$API_KEY&region=US&with_original_language=en" to "Popular Movies",
        "/trending/tv/week?api_key=$API_KEY&region=US&with_original_language=en" to "Popular TV Shows",
        "/tv/airing_today?api_key=$API_KEY&region=US&with_original_language=en" to "Airing Today TV Shows",
        "/discover/tv?api_key=$API_KEY&with_networks=213" to "Netflix",
        "/discover/tv?api_key=$API_KEY&with_networks=1024" to "Amazon",
        "/discover/tv?api_key=$API_KEY&with_networks=2739" to "Disney+",
        "/discover/tv?api_key=$API_KEY&with_networks=453" to "Hulu",
        "/discover/tv?api_key=$API_KEY&with_networks=2552" to "Apple TV+",
        "/discover/tv?api_key=$API_KEY&with_networks=49" to "HBO",
        "/discover/tv?api_key=$API_KEY&with_original_language=ko" to "Korean Shows",
        "/movie/top_rated?api_key=$API_KEY&region=US" to "Top Rated Movies",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tmdbAPI = getApiBase()
        val adultQuery = if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("$tmdbAPI${request.data}$adultQuery&language=$langCode&page=$page", timeout = 10000)
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
            this.score = Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val tmdbAPI = getApiBase()
        return app.get("$tmdbAPI/search/multi?api_key=$API_KEY&language=$langCode&query=$query&page=$page&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }?.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val tmdbAPI = getApiBase()
        val data = parseJson<Data>(url)
        val type = if (data.type == "movie") TvType.Movie else TvType.TvSeries
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$API_KEY&language=$langCode&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$API_KEY&language=$langCode&append_to_response=$append"
        }

        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
        
        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        
        val genres = res.genres?.mapNotNull { it.name }
        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }
        val tags = keywords?.map { it.replaceFirstChar { char -> char.titlecase() } }?.takeIf { it.isNotEmpty() } ?: genres

        val actors = res.credits?.cast?.mapNotNull { cast ->
            val name = cast.name ?: cast.originalName ?: return@mapNotNull null
            com.lagradost.cloudstream3.ActorData(
                com.lagradost.cloudstream3.Actor(name, getImageUrl(cast.profilePath)), roleString = cast.character
            )
        } ?: emptyList()

        val recommendations = res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }
        val trailer = res.videos?.results.orEmpty()
            .filter { it.type == "Trailer" }
            .map { "https://www.youtube.com/watch?v=${it.key}" }
            .reversed()

        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$API_KEY&language=$langCode")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            LinkData(
                                id = data.id,
                                imdbId = res.external_ids?.imdb_id,
                                type = data.type,
                                season = eps.seasonNumber,
                                episode = eps.episodeNumber,
                                title = title,
                                year = year,
                                orgTitle = orgTitle,
                                epsTitle = eps.name,
                                date = season.airDate,
                            ).toJson()
                        ) {
                            this.name = eps.name
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.score = Score.from10(eps.voteAverage)
                            this.description = eps.overview
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = tags
                this.score = Score.from10(res.vote_average.toString())
                this.showStatus = if(res.status == "Returning Series") ShowStatus.Ongoing else ShowStatus.Completed
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    id = data.id,
                    imdbId = res.external_ids?.imdb_id,
                    type = data.type,
                    title = title,
                    year = year,
                    orgTitle = orgTitle
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = tags
                this.score = Score.from10(res.vote_average.toString())
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        
        val disabledProviderIds = sharedPref.getStringSet("disabled_providers", emptySet()) ?: emptySet()
        val providersList = buildProviders().filter { it.id !in disabledProviderIds }

        // Mempersiapkan array fungsi suspend untuk dijalankan secara paralel
        val tasks = mutableListOf<suspend () -> Unit>()
        
        // Task Subtitle
        tasks.add { invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback) }
        tasks.add { invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback) }
        
        // Task Providers
        providersList.forEach { provider ->
            tasks.add { 
                provider.invoke(res, subtitleCallback, callback)
            }
        }

        // Jalankan semua task
        runAllAsync(*tasks.toTypedArray())

        return true
    }

    // --- DATA CLASSES ---

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
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
    )

    data class Genres(
        @JsonProperty("name") val name: String? = null,
    )

    data class Keywords(
        @JsonProperty("name") val name: String? = null,
    )

    data class KeywordResults(
        @JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    )

    data class Seasons(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
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

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("type") val type: String? = null,
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null,
    )

    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class LastEpisodeToAir(
        @JsonProperty("season_number") val season_number: Int? = null,
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
    
    data class TmdbDate(
        val today: String,
        val nextWeek: String,
        val lastWeekStart: String
    )
}
