package com.AdiManuLateri3

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
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
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newAnimeLoadResponse
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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

open class Lateri3Play(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "Lateri3Play"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    // Token management (opsional, jika nanti butuh fitur login sederhana)
    val token: String? = sharedPref?.getString("token", null)
    val langCode = sharedPref?.getString("tmdb_language_code", "id-ID") ?: "en-US"

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        /** CONFIG & API KEYS */
        private const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        // Key baru sesuai permintaan
        private const val apiKey = BuildConfig.TMDB_API 
        
        // Proxy list untuk bypass blokir TMDb di Indonesia
        private const val REMOTE_PROXY_LIST = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Proxylist.txt"
        private var currentBaseUrl: String? = null

        /** 10 SELECTED PROVIDERS URLS (HARDCODED FOR STABILITY) */
        const val UHDMoviesAPI = "https://uhdmovies.fyi"
        const val MultiMoviesAPI = "https://multimovies.sbs" // Backup: multimovies.cloud
        const val NineTvAPI = "https://moviesapi.club"
        const val RidoMoviesAPI = "https://ridomovies.tv"
        const val ZoeChipAPI = "https://www1.zoechip.to"
        const val NepuAPI = "https://nepu.to"
        const val PlayDesiAPI = "https://playdesi.net"
        const val MoflixAPI = "https://moflix-stream.xyz"
        const val VidsrcAPI = "https://vidsrc.cc"
        const val WatchSomuchAPI = "https://watchsomuch.tv"

        /** SUBTITLE APIS */
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val WyZIESUBAPI = "https://sub.wyzie.ru"

        // Helper untuk TMDb Proxy Rotation
        suspend fun getApiBase(): String {
            currentBaseUrl?.let { return it }
            if (isOfficialAvailable()) {
                currentBaseUrl = OFFICIAL_TMDB_URL
                return OFFICIAL_TMDB_URL
            }
            val proxies = fetchProxyList()
            for (proxy in proxies) {
                if (isProxyWorking(proxy)) {
                    currentBaseUrl = proxy
                    Log.d("TMDB", "✅ Switched to proxy: $proxy")
                    return proxy
                }
            }
            return OFFICIAL_TMDB_URL
        }

        private suspend fun isOfficialAvailable(): Boolean {
            val testUrl = "$OFFICIAL_TMDB_URL/configuration?api_key=$apiKey"
            return withTimeoutOrNull(3000) {
                try {
                    val response = app.get(testUrl, timeout = 2000)
                    response.isSuccessful
                } catch (e: Exception) { false }
            } ?: false
        }

        private suspend fun isProxyWorking(proxyUrl: String): Boolean {
            val testUrl = "$proxyUrl/configuration?api_key=$apiKey"
            return withTimeoutOrNull(3000) {
                try {
                    val response = app.get(testUrl, timeout = 2000)
                    response.isSuccessful
                } catch (e: Exception) { false }
            } ?: false
        }

        private suspend fun fetchProxyList(): List<String> = try {
            val response = app.get(REMOTE_PROXY_LIST).text
            val json = JSONObject(response)
            val arr = json.getJSONArray("proxies")
            List(arr.length()) { arr.getString(it).trim().removeSuffix("/") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) { emptyList() }

        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    // Halaman Utama CloudStream
    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "/movie/popular?api_key=$apiKey&region=US" to "Popular Movies",
        "/tv/popular?api_key=$apiKey&region=US" to "Popular TV Shows",
        "/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Dramas",
        "/discover/tv?api_key=$apiKey&with_keywords=210024|222243" to "Anime",
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
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=$langCode&query=$query&page=$page&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }?.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val tmdbAPI = getApiBase()
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = "$tmdbAPI/${if (type == TvType.Movie) "movie" else "tv"}/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"

        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
            
        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val genres = res.genres?.mapNotNull { it.name }

        val isCartoon = genres?.contains("Animation") ?: false
        val isAnime = isCartoon && (res.original_language == "ja")
        val isAsian = !isAnime && (res.original_language == "zh" || res.original_language == "ko")
        val isBollywood = res.production_countries?.any { it.name == "India" } ?: false

        val actors = res.credits?.cast?.mapNotNull { cast ->
            val name = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(Actor(name, getImageUrl(cast.profilePath)), roleString = cast.character)
        } ?: emptyList()

        val recommendations = res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }
        val trailer = res.videos?.results.orEmpty()
            .filter { it.type == "Trailer" }
            .map { "https://www.youtube.com/watch?v=${it.key}" }

        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey&language=$langCode")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                res.external_ids?.tvdb_id,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                title = title,
                                year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                orgTitle = orgTitle,
                                isAnime = isAnime,
                                epsTitle = eps.name,
                                date = season.airDate,
                                isAsian = isAsian,
                                isBollywood = isBollywood,
                                isCartoon = isCartoon
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

            return if (isAnime) {
                newAnimeLoadResponse(title, url, TvType.Anime) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = genres
                    this.score = Score.from10(res.vote_average.toString())
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    addTrailer(trailer)
                    addTMDbId(data.id.toString())
                    addImdbId(res.external_ids?.imdb_id)
                    addEpisodes(com.lagradost.cloudstream3.DubStatus.Subbed, episodes)
                }
            } else {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = genres
                    this.score = Score.from10(res.vote_average.toString())
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    addTrailer(trailer)
                    addTMDbId(data.id.toString())
                    addImdbId(res.external_ids?.imdb_id)
                }
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    data.id,
                    res.external_ids?.imdb_id,
                    res.external_ids?.tvdb_id,
                    data.type,
                    title = title,
                    year = year,
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    isAsian = isAsian,
                    isBollywood = isBollywood,
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = genres
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
        
        // Cek provider yang dimatikan user
        val disabledProviderIds = sharedPref
            ?.getStringSet("disabled_providers", emptySet())
            ?.toSet() ?: emptySet()

        // Ambil daftar 10 Provider dari ProvidersList.kt
        val providersList = buildProviders().filter { it.id !in disabledProviderIds }

        // Eksekusi secara paralel: Subtitle + Video Extractors
        runAllAsync(
            {
                // Wyzie Subs
                invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback)
            },
            {
                // OpenSubtitles V3
                invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback)
            },
            *providersList.map { provider ->
                suspend {
                    // Panggil fungsi invoke milik provider masing-masing
                    provider.invoke(res, subtitleCallback, callback)
                }
            }.toTypedArray()
        )

        return true
    }

    // DATA CLASSES UNTUK PARSING METADATA
    
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
        val isAnime: Boolean = false,
        val epsTitle: String? = null,
        val date: String? = null,
        val isAsian: Boolean = false,
        val isBollywood: Boolean = false,
        val isCartoon: Boolean = false,
    )

    data class Data(
        val id: Int? = null,
        val type: String? = null,
    )

    data class Results(
        @param:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @param:JsonProperty("id") val id: Int? = null,
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("title") val title: String? = null,
        @param:JsonProperty("original_title") val originalTitle: String? = null,
        @param:JsonProperty("media_type") val mediaType: String? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("vote_average") val voteAverage: Double? = null,
    )

    data class Genres(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
    )

    data class Seasons(
        @get:JsonProperty("season_number") val seasonNumber: Int? = null,
        @get:JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("original_name") val originalName: String? = null,
        @get:JsonProperty("character") val character: String? = null,
        @get:JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("overview") val overview: String? = null,
        @get:JsonProperty("air_date") val airDate: String? = null,
        @get:JsonProperty("still_path") val stillPath: String? = null,
        @get:JsonProperty("vote_average") val voteAverage: Double? = null,
        @get:JsonProperty("episode_number") val episodeNumber: Int? = null,
        @get:JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetailEpisodes(
        @get:JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @get:JsonProperty("key") val key: String? = null,
        @get:JsonProperty("type") val type: String? = null,
    )

    data class ResultsTrailer(
        @get:JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @get:JsonProperty("imdb_id") val imdb_id: String? = null,
        @get:JsonProperty("tvdb_id") val tvdb_id: Int? = null,
    )

    data class Credits(
        @get:JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @get:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class ProductionCountries(
        @get:JsonProperty("name") val name: String? = null,
    )

    data class MediaDetail(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("title") val title: String? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("original_title") val originalTitle: String? = null,
        @get:JsonProperty("original_name") val originalName: String? = null,
        @get:JsonProperty("poster_path") val posterPath: String? = null,
        @get:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @get:JsonProperty("release_date") val releaseDate: String? = null,
        @get:JsonProperty("first_air_date") val firstAirDate: String? = null,
        @get:JsonProperty("overview") val overview: String? = null,
        @get:JsonProperty("runtime") val runtime: Int? = null,
        @get:JsonProperty("vote_average") val vote_average: Any? = null,
        @get:JsonProperty("original_language") val original_language: String? = null,
        @get:JsonProperty("status") val status: String? = null,
        @get:JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @get:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @get:JsonProperty("videos") val videos: ResultsTrailer? = null,
        @get:JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @get:JsonProperty("credits") val credits: Credits? = null,
        @get:JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
        @get:JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
    )
}
