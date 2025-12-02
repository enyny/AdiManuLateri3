package com.AdiManuLateri3

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newHomePageResponse // Pastikan ini diimport
import com.lagradost.cloudstream3.newEpisode // Pastikan ini diimport
import com.lagradost.cloudstream3.newMovieLoadResponse // Pastikan ini diimport
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePrimeSrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRiveStream
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeSubtitleAPI
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUqloadsXyz
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWyZIESUBAPI
import com.AdiManuLateri3.Lateri3PlayUtils.runLimitedAsync
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitAll // Tambahkan ini
import org.json.JSONObject

// Import BuildConfig dari paket ini
import com.AdiManuLateri3.BuildConfig

open class Lateri3Play(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "Lateri3Play"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    val langCode = sharedPref?.getString("tmdb_language_code", "en-US")
    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        private const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        // Ganti URL ini dengan URL raw yang valid atau gunakan string kosong jika belum ada
        private const val REMOTE_PROXY_LIST = "https://raw.githubusercontent.com/AdiManuLateri3/Lateri3Play/main/Proxylist.txt" 
        private const val apiKey = BuildConfig.TMDB_API // Menggunakan BuildConfig
        private const val simkl = "https://api.simkl.com"
        private var currentBaseUrl: String? = null

        private val apiMutex = Mutex()
        private const val TAG = "Lateri3Play"

        const val PrimeSrcApi = "https://primesrc.me"
        const val RiveStreamAPI = "https://rivestream.org"
        const val UqloadsAPI = "https://uqloads.xyz"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val WyZIESUBAPI = "https://sub.wyzie.ru"


        suspend fun getApiBase(): String {
            currentBaseUrl?.let { return it }
            return apiMutex.withLock {
                currentBaseUrl?.let { return it }

                if (checkConnectivity(OFFICIAL_TMDB_URL)) {
                    currentBaseUrl = OFFICIAL_TMDB_URL
                    return OFFICIAL_TMDB_URL
                }

                val proxies = fetchProxyList()
                if (proxies.isEmpty()) {
                    return OFFICIAL_TMDB_URL
                }

                val workingProxy = coroutineScope {
                    val deferredChecks = proxies.map { proxy ->
                        async {
                            if (checkConnectivity(proxy)) proxy else null
                        }
                    }
                    deferredChecks.awaitAll().firstOrNull { it != null }
                }

                if (workingProxy != null) {
                    currentBaseUrl = workingProxy
                    return workingProxy
                }

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
            emptyList()
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
        "/discover/movie?api_key=$apiKey&with_keywords=210024|222243" to "Anime Movies",
        "/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tmdbAPI =getApiBase()
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        
        // Memperbaiki inferensi tipe dengan <Results>
        val home = app.get("$tmdbAPI${request.data}$adultQuery&language=$langCode&page=$page", timeout = 10000)
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid Json reponse")
            
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        val mediaId = id
        val mediaTitle = title ?: name ?: originalTitle ?: return null
        val mediaType = mediaType ?: type
        
        if (mediaId == null) return null

        return newMovieSearchResponse(
            mediaTitle,
            Data(id = mediaId, type = mediaType).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
            this.score = voteAverage?.let { Score.from10(it) }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query,1)?.items

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
        val poster = getImageUrl(res.posterPath)
        val bgPoster = getImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val genres = res.genres?.mapNotNull { it.name }

        val isAsian = res.original_language == "ko" || res.original_language == "zh"
        val isBollywood = res.production_countries?.any { it.name == "India" } ?: false
        
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

        // Hapus addSimklId jika menyebabkan error akses privat atau unresolved
        // val simklid = ... (Dihapus untuk penyederhanaan dan menghindari error akses)

        if (type == TvType.TvSeries) {
            val episodes = coroutineScope {
                res.seasons?.map { season ->
                    async {
                        // Tentukan tipe eksplisit untuk parsedSafe
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
                                        title = title,
                                        year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                        orgTitle = orgTitle,
                                        isAsian = isAsian,
                                        isBollywood = isBollywood,
                                    ).toJson()
                                ) {
                                    this.name = eps.name
                                    this.season = eps.seasonNumber
                                    this.episode = eps.episodeNumber
                                    this.posterUrl = getImageUrl(eps.stillPath)
                                    this.score = eps.voteAverage?.let { Score.from10(it) }
                                    this.description = eps.overview
                                    this.runTime = eps.runTime
                                    this.addDate(eps.airDate) // Pastikan fungsi addDate diimport
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
                this.score = res.vote_average?.toString()?.let { Score.from10(it) }
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer) // Jika overload list string tidak ada, coba addTrailer(trailer.firstOrNull())
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
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
                    isAsian = isAsian,
                    isBollywood = isBollywood,
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords?.map { word -> word.replaceFirstChar { it.titlecase() } }
                    ?.takeIf { it.isNotEmpty() } ?: genres

                this.score = res.vote_average?.toString()?.let { Score.from10(it) }
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

        runLimitedAsync(concurrency = 5,
            {
                invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback)
            },
            {
                invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback)
            },
            {
                invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
            },
            {
                invokeUqloadsXyz(res.imdbId, res.season, res.episode, subtitleCallback, callback)
            },
            {
                invokeRiveStream(res.id, res.season, res.episode, callback)
            }
        )

        return true
    }

    // LinkData dan Data Classes lain (Tetap sama seperti sebelumnya)
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
        val isAsian: Boolean = false,
        val isBollywood: Boolean = false,
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
