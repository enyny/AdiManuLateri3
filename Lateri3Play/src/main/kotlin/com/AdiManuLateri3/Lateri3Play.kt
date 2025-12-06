package com.AdiManuLateri3

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
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
    override var lang = "id"
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime
    )

    val langCode = sharedPref.getString("tmdb_language_code", "en-US")

    companion object {
        private const val TMDB_API_URL = "https://api.themoviedb.org/3"
        // Menggunakan API Key default
        private const val API_KEY = "1cfadd9dbfc534abf6de40e1e7eaf4c7"

        fun getApiBase(): String = TMDB_API_URL
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
        val append = "alternative_titles,credits,external_ids,videos,recommendations,keywords"

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
        
        // Logika Deteksi Kategori (Anime/Asian/Bollywood)
        val isCartoon = genres?.contains("Animation") ?: false
        val isAnime = isCartoon && (res.originalLanguage == "ja" || res.originalLanguage == "jp" || (res.credits?.cast?.any { it.originalName?.matches(Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]+")) == true } == true))
        val isAsian = !isAnime && (res.originalLanguage == "ko" || res.originalLanguage == "zh")
        val isBollywood = res.credits?.cast?.any { it.name == "Shah Rukh Khan" || it.name == "Salman Khan" } ?: false || (genres?.contains("Bollywood") == true)
        
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
                                isAnime = isAnime,
                                isAsian = isAsian,
                                isBollywood = isBollywood
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

            return newTvSeriesLoadResponse(title, url, if (isAnime) TvType.Anime else TvType.TvSeries, episodes) {
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
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    isAsian = isAsian,
                    isBollywood = isBollywood
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
        // Mengambil daftar provider yang sudah diperbarui di ProvidersList.kt (tanpa Yflix)
        val providersList = buildProviders().filter { it.id !in disabledProviderIds }

        // Eksekusi semua provider secara paralel
        val tasks = mutableListOf<suspend () -> Unit>()
        
        // 1. Subtitle API Global
        tasks.add { com.AdiManuLateri3.Lateri3PlayExtractor.invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback) }
        tasks.add { com.AdiManuLateri3.Lateri3PlayExtractor.invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback) }
        
        // 2. Movie/Series Providers Loop
        providersList.forEach { provider ->
            tasks.add { 
                provider.invoke(res, subtitleCallback, callback)
            }
        }

        // Jalankan semua tugas pencarian link secara bersamaan
        runAllAsync(*tasks.toTypedArray())

        return true
    }
}
