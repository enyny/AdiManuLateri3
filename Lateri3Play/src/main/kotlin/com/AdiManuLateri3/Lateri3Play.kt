package com.AdiManuLateri3

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
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
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWyZIESUBAPI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

open class Lateri3Play(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "Lateri3Play"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    val token: String? = sharedPref?.getString("token", null)
    val langCode = sharedPref?.getString("tmdb_language_code", "en-US")

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        /** TOOLS */
        private const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        private const val REMOTE_PROXY_LIST = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Proxylist.txt"
        // Menggunakan API Key dari BuildConfig (hasil hardcode di build.gradle.kts)
        private const val apiKey = BuildConfig.TMDB_API 
        private const val simkl = "https://api.simkl.com"
        private var currentBaseUrl: String? = null

        private val apiMutex = Mutex()
        private const val TAG = "Lateri3Play"

        // Mekanisme rotasi proxy agar tahan banting
        suspend fun getApiBase(): String {
            currentBaseUrl?.let { return it }
            return apiMutex.withLock {
                currentBaseUrl?.let { return it }

                // 1. Coba Official
                if (checkConnectivity(OFFICIAL_TMDB_URL)) {
                    Log.d(TAG, "✅ Using official TMDB API")
                    currentBaseUrl = OFFICIAL_TMDB_URL
                    return OFFICIAL_TMDB_URL
                }

                // 2. Fetch Proxies dari GitHub
                val proxies = fetchProxyList()
                if (proxies.isEmpty()) {
                    Log.e(TAG, "❌ No proxies found, falling back to official")
                    return OFFICIAL_TMDB_URL
                }

                // 3. Cek Proxy secara Parallel
                val workingProxy = coroutineScope {
                    val deferredChecks = proxies.map { proxy ->
                        async {
                            if (checkConnectivity(proxy)) proxy else null
                        }
                    }
                    deferredChecks.awaitAll().firstOrNull { it != null }
                }

                if (workingProxy != null) {
                    Log.d(TAG, "✅ Switched to proxy: $workingProxy")
                    currentBaseUrl = workingProxy
                    return workingProxy
                }

                // 4. Fallback Terakhir
                Log.e(TAG, "❌ All proxies failed, fallback to official")
                currentBaseUrl = OFFICIAL_TMDB_URL
                OFFICIAL_TMDB_URL
            }
        }

        private suspend fun checkConnectivity(url: String): Boolean {
            val testUrl = "$url/configuration?api_key=$apiKey"
            return withTimeoutOrNull(2000) { 
                try {
                    val response = app.get(
                        testUrl,
                        timeout = 1500,
                        headers = mapOf("Cache-Control" to "no-cache")
                    )
                    response.code == 200 || response.code == 304
                } catch (e: Exception) {
                    false
                }
            } ?: false
        }

        private suspend fun fetchProxyList(): List<String> = try {
            val response = app.get(REMOTE_PROXY_LIST, timeout = 5000).text
            val json = JSONObject(response)
            val arr = json.getJSONArray("proxies")
            (0 until arr.length()).map { arr.getString(it).trim().removeSuffix("/") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching proxy list: ${e.message}")
            emptyList()
        }

        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        private var cachedDomains: DomainsParser? = null

        suspend fun getDomains(forceRefresh: Boolean = false): DomainsParser? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<DomainsParser>()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return cachedDomains
        }

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

    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "/trending/movie/week?api_key=$apiKey&region=US&with_original_language=en" to "Popular Movies",
        "/trending/tv/week?api_key=$apiKey&region=US&with_original_language=en" to "Popular TV Shows",
        "/tv/airing_today?api_key=$apiKey&region=US&with_original_language=en" to "Airing Today TV Shows",
        "/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix",
        "/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon",
        "/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+",
        "/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu",
        "/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+",
        "/discover/tv?api_key=$apiKey&with_networks=49" to "HBO",
        "/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
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
            } ?: throw ErrorLoadingException("Invalid Json response")
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

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1)?.items

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

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
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

        val actors = res.credits?.cast?.mapNotNull { cast ->
            val name = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                Actor(name, getImageUrl(cast.profilePath)), roleString = cast.character
            )
        } ?: emptyList()

        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results.orEmpty()
            .filter { it.type == "Trailer" }
            .map { "https://www.youtube.com/watch?v=${it.key}" }
            .reversed()
            .ifEmpty {
                res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" } ?: emptyList()
            }

        val simklid = coroutineScope {
            async {
                runCatching {
                    res.external_ids?.imdb_id?.takeIf { it.isNotBlank() }?.let { imdb ->
                        val path = if (type == TvType.Movie) "movies" else "tv"
                        val resJson =
                            JSONObject(app.get("$simkl/$path/$imdb?client_id=${com.lagradost.cloudstream3.BuildConfig.SIMKL_CLIENT_ID}").text)
                        resJson.optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
                    }
                }.getOrNull()
            }
        }

        if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = coroutineScope {
                res.seasons?.map { season ->
                    async {
                        app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey&language=$langCode")
                            .parsedSafe<MediaDetailEpisodes>()
                            ?.episodes
                            ?.map { eps ->
                                newEpisode(
                                    LinkData(
                                        data.id,
                                        res.external_ids?.imdb_id,
                                        res.external_ids?.tvdb_id,
                                        data.type,
                                        eps.seasonNumber,
                                        eps.episodeNumber,
                                        eps.id,
                                        title = title,
                                        year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                        orgTitle = orgTitle,
                                        airedYear = year,
                                        lastSeason = lastSeason,
                                        epsTitle = eps.name,
                                        date = season.airDate,
                                        airedDate = res.releaseDate ?: res.firstAirDate,
                                        alttitle = res.title,
                                        nametitle = res.name
                                    ).toJson()
                                ) {
                                    this.name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                                    this.season = eps.seasonNumber
                                    this.episode = eps.episodeNumber
                                    this.posterUrl = getImageUrl(eps.stillPath)
                                    this.score = Score.from10(eps.voteAverage)
                                    this.description = eps.overview
                                    this.runTime = eps.runTime
                                }.apply {
                                    this.addDate(eps.airDate)
                                }
                            }
                    }
                }?.awaitAll()?.filterNotNull()?.flatten() ?: listOf()
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = keywords?.map { word -> word.replaceFirstChar { it.titlecase() } }
                    ?.takeIf { it.isNotEmpty() } ?: genres
                this.score = Score.from10(res.vote_average.toString())
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
                addSimklId(simklid.await())
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
                    airedDate = res.releaseDate ?: res.firstAirDate,
                    alttitle = res.title,
                    nametitle = res.name
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.comingSoon = isUpcoming(releaseDate)
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords?.map { word -> word.replaceFirstChar { it.titlecase() } }
                    ?.takeIf { it.isNotEmpty() } ?: genres

                this.score = Score.from10(res.vote_average.toString())
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
                addSimklId(simklid.await())
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
        
        // Membangun daftar provider sesuai permintaan (Hanya Lateri3Play)
        val providersList = buildProviders()
        val authToken = token
        
        runLimitedAsync(concurrency = 10,
            {
                // Subtitle Wyzie (Requested)
                try {
                    invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback)
                } catch (_: Throwable) {}
            },
            *providersList.map { provider ->
                suspend {
                    try {
                        provider.invoke(
                            res,
                            subtitleCallback,
                            callback,
                            authToken ?: "",
                            "" // dahmerMoviesAPI placeholder, not used if not needed or will be filled from BuildConfig in Extractor
                        )
                    } catch (_: Throwable) {}
                }
            }.toTypedArray()
        )

        return true
    }

    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val epid: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val orgTitle: String? = null,
        val airedYear: Int? = null,
        val lastSeason: Int? = null,
        val epsTitle: String? = null,
        val date: String? = null,
        val airedDate: String? = null,
        val alttitle: String? = null,
        val nametitle: String? = null,
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

    data class Keywords(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
    )

    data class KeywordResults(
        @get:JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
        @get:JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    )

    data class Seasons(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("season_number") val seasonNumber: Int? = null,
        @get:JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("original_name") val originalName: String? = null,
        @get:JsonProperty("character") val character: String? = null,
        @get:JsonProperty("known_for_department") val knownForDepartment: String? = null,
        @get:JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("overview") val overview: String? = null,
        @get:JsonProperty("air_date") val airDate: String? = null,
        @get:JsonProperty("still_path") val stillPath: String? = null,
        @get:JsonProperty("vote_average") val voteAverage: Double? = null,
        @get:JsonProperty("episode_number") val episodeNumber: Int? = null,
        @get:JsonProperty("season_number") val seasonNumber: Int? = null,
        @get:JsonProperty("runtime") val runTime: Int? = null
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

    data class AltTitles(
        @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
        @get:JsonProperty("title") val title: String? = null,
        @get:JsonProperty("type") val type: String? = null,
    )

    data class ResultsAltTitles(
        @get:JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
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

    data class LastEpisodeToAir(
        @get:JsonProperty("episode_number") val episode_number: Int? = null,
        @get:JsonProperty("season_number") val season_number: Int? = null,
    )

    data class ProductionCountries(
        @get:JsonProperty("name") val name: String? = null,
    )

    data class MediaDetail(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("imdb_id") val imdbId: String? = null,
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
        @get:JsonProperty("keywords") val keywords: KeywordResults? = null,
        @get:JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
        @get:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @get:JsonProperty("videos") val videos: ResultsTrailer? = null,
        @get:JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @get:JsonProperty("credits") val credits: Credits? = null,
        @get:JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
        @get:JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
        @get:JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
    )
}
