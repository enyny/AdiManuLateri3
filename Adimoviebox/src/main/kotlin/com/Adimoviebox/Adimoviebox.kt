package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

// Import wajib
import com.lagradost.cloudstream3.utils.INFER_TYPE 

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://h5-api.aoneroom.com"
    private val playApiUrl = "https://filmboom.top"
    
    // API Key TMDB (Public/Shared Key - Sebaiknya ganti dengan milik sendiri jika limit habis)
    private val tmdbApiKey = "95f089a42da44321946c96562092d6e6" 
    private val tmdbBaseUrl = "https://api.themoviedb.org/3"
    
    override val instantLinkLoading = true
    override var name = "Adimoviebox (TMDB)"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    private val commonHeaders = mapOf(
        "accept" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
        "x-request-lang" to "en",
        "content-type" to "application/json",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "Trending🔥" to "Trending🔥",
        "Trending Indonesian Movies" to "Trending Indonesian Movies",
        "Trending Indonesian Drama💗" to "Trending Indonesian Drama💗",
        "Hollywood Movies" to "Hollywood Movies",
        "K-Drama: New Release" to "K-Drama: New Release",
        "Into Animeverse🌟" to "Into Animeverse🌟",
        "Western TV" to "Western TV",
        "Animated Flim" to "Animated Flim"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val response = app.get(
            "$apiUrl/wefeed-h5api-bff/home?host=moviebox.ph", 
            headers = commonHeaders
        ).parsedSafe<HomeResponse>()

        val targetCategory = response?.data?.operatingList?.find { 
            it.title?.trim() == request.name.trim() 
        }

        val filmList = targetCategory?.subjects?.mapNotNull {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Kategori tidak ditemukan")

        return newHomePageResponse(request.name, filmList)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$apiUrl/wefeed-h5api-bff/subject/search-suggest", 
            requestBody = mapOf(
                "keyword" to query,
                "perPage" to 20
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()),
            headers = commonHeaders
        ).parsedSafe<Media>()?.data?.items?.mapNotNull { 
            it.toSearchResponse(this) 
        } ?: throw ErrorLoadingException("Search failed")
    }

    // --- FUNGSI LOAD MODIFIKASI (TMDB INTEGRATION) ---
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        // 1. Ambil Data ASLI MovieBox (Wajib untuk Link)
        val document = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$id",
            headers = commonHeaders
        ).parsedSafe<MediaDetail>()?.data ?: throw ErrorLoadingException("Gagal memuat data MovieBox")

        val subject = document.subject
        val originalTitle = subject?.title ?: ""
        val originalYear = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val isSeries = subject?.subjectType == 2
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie
        
        // --- 2. LOGIKA HYBRID TMDB ---
        var finalPoster = subject?.cover?.url
        var finalPlot = subject?.description
        var finalBackground: String? = null
        var finalScore = Score.from10(subject?.imdbRatingValue?.toString())
        var finalTags = subject?.genre?.split(",")?.map { it.trim() }
        var finalActors: List<ActorData>? = null
        var finalRecommendations: List<SearchResponse>? = null
        var tmdbId: Int? = null

        try {
            // A. Cari ID TMDB berdasarkan Judul & Tahun
            val searchType = if (isSeries) "tv" else "movie"
            val query = URLEncoder.encode(originalTitle, "UTF-8")
            val searchUrl = "$tmdbBaseUrl/search/$searchType?api_key=$tmdbApiKey&query=$query"
            
            val tmdbSearch = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
            
            // Cari hasil yang paling cocok (match Title & Year)
            val match = tmdbSearch?.results?.find { 
                val releaseDate = it.release_date ?: it.first_air_date
                val year = releaseDate?.substringBefore("-")?.toIntOrNull()
                // Toleransi tahun +/- 1 karena kadang tanggal rilis beda negara
                year == originalYear || year == (originalYear?.plus(1)) || year == (originalYear?.minus(1))
            } ?: tmdbSearch?.results?.firstOrNull() // Fallback ke hasil pertama jika tidak ada match tahun

            if (match != null) {
                tmdbId = match.id
                
                // B. Ambil Detail Lengkap TMDB (Credits, Recommendations, Images)
                val detailUrl = "$tmdbBaseUrl/$searchType/${match.id}?api_key=$tmdbApiKey&append_to_response=credits,recommendations,images"
                val tmdbDetail = app.get(detailUrl).parsedSafe<TmdbDetailResponse>()

                if (tmdbDetail != null) {
                    // Update Metadata dengan Data TMDB
                    finalPoster = "https://image.tmdb.org/t/p/w500${tmdbDetail.poster_path}"
                    if (tmdbDetail.backdrop_path != null) {
                        finalBackground = "https://image.tmdb.org/t/p/original${tmdbDetail.backdrop_path}"
                    }
                    finalPlot = tmdbDetail.overview
                    finalScore = Score.from10(tmdbDetail.vote_average?.toString())
                    finalTags = tmdbDetail.genres?.map { it.name ?: "" }

                    // Actors dari TMDB
                    finalActors = tmdbDetail.credits?.cast?.mapNotNull { 
                        if (it.profile_path == null) return@mapNotNull null
                        ActorData(
                            Actor(it.name ?: "Unknown", "https://image.tmdb.org/t/p/w185${it.profile_path}"),
                            roleString = it.character
                        )
                    }

                    // Rekomendasi dari TMDB (Perlu dimapping ulang ke SearchResponse dummy karena linknya tidak bisa diklik lgsg ke MovieBox)
                    // *Catatan: Rekomendasi TMDB sulit di-link ke MovieBox tanpa searching ulang.
                    // Jadi sebaiknya kita tetap pakai rekomendasi MovieBox agar bisa diklik.
                    // Namun, jika ingin tampilan saja, bisa pakai TMDB.
                    // KEPUTUSAN: Tetap pakai rekomendasi MovieBox agar UX lancar.
                }
            }
        } catch (e: Exception) {
            System.out.println("TMDB_ERROR: ${e.message}")
            // Jika error TMDB, biarkan menggunakan data MovieBox (Fallback)
        }

        // --- 3. PROSES REKOMENDASI ASLI MOVIEBOX (Agar bisa diklik) ---
        if (finalRecommendations == null) {
            finalRecommendations = app.get(
                "$apiUrl/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
                headers = commonHeaders
            ).parsedSafe<Media>()?.data?.items?.mapNotNull {
                it.toSearchResponse(this)
            }
        }

        // Fallback aktor dari MovieBox jika TMDB gagal
        if (finalActors == null) {
            finalActors = document.stars?.mapNotNull { cast ->
                ActorData(
                    Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                    roleString = cast.character
                )
            }?.distinctBy { it.actor }
        }

        // --- 4. RETURN LOAD RESPONSE ---
        // Kita menggunakan Metadata (Poster, Plot, dll) dari TMDB (variable 'final...')
        // TAPI Data LoadData (String JSON) MENGGUNAKAN ID MOVIEBOX ASLI
        
        return if (tvType == TvType.TvSeries) {
            val episode = document.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id, // ID Asli MovieBox
                                seasons.se,
                                episode,
                                subject?.detailPath
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                            // Jika mau thumbnail episode dari TMDB, butuh request tambahan per season.
                            // Untuk efisiensi, biarkan default atau tambah logika nanti.
                        }
                    }
            }?.flatten() ?: emptyList()
            
            newTvSeriesLoadResponse(originalTitle, url, TvType.TvSeries, episode) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = finalBackground
                this.year = originalYear
                this.plot = finalPlot
                this.tags = finalTags
                this.score = finalScore
                this.actors = finalActors
                this.recommendations = finalRecommendations
                this.tmdbId = tmdbId // Memberitahu CloudStream ID TMDB-nya (berguna untuk fitur tracking)
            }
        } else {
            newMovieLoadResponse(
                originalTitle,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = finalBackground
                this.year = originalYear
                this.plot = finalPlot
                this.tags = finalTags
                this.score = finalScore
                this.actors = finalActors
                this.recommendations = finalRecommendations
                this.tmdbId = tmdbId
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Logika Link TETAP SAMA dengan yang sudah diperbaiki (Debug Version atau Clean Version)
        // Karena kita mengirim 'LoadData' yang berisi ID MovieBox, fungsi ini akan bekerja normal.

        val media = parseJson<LoadData>(data)
        val playReferer = "$playApiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&detailSe=&detailEp=&lang=en"

        val playHeaders = mapOf(
            "authority" to "filmboom.top",
            "accept" to "application/json",
            "origin" to "https://filmboom.top",
            "referer" to playReferer,
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
            "x-request-lang" to "en"
        )

        val targetUrl = "$playApiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detail_path=${media.detailPath}"

        // Debug Log
        System.out.println("ADILOG_TARGET: $targetUrl")

        val responseText = try {
            app.get(targetUrl, headers = playHeaders).text
        } catch (e: Exception) {
            System.out.println("ADILOG_ERR: ${e.message}")
            return false
        }
        
        System.out.println("ADILOG_RES: $responseText")

        val response = try { parseJson<Media>(responseText) } catch (e: Exception) { null }
        val streams = response?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$playApiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val videoId = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format

        if (videoId != null) {
            val captionUrl = "$playApiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$videoId&subjectId=${media.id}"
            app.get(captionUrl, headers = playHeaders).parsedSafe<Media>()?.data?.captions?.forEach { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        subtitle.lanName ?: "Unknown",
                        subtitle.url ?: return@forEach
                    )
                )
            }
        }
        return true
    }
}

// --- DATA CLASSES MOVIEBOX ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
)

data class HomeResponse(@JsonProperty("data") val data: HomeData? = null)
data class HomeData(@JsonProperty("operatingList") val operatingList: ArrayList<HomeModule>? = arrayListOf())
data class HomeModule(@JsonProperty("title") val title: String? = null, @JsonProperty("subjects") val subjects: ArrayList<Items>? = arrayListOf())

data class Media(@JsonProperty("data") val data: Data? = null) {
    data class Data(
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(@JsonProperty("id") val id: String? = null, @JsonProperty("format") val format: String? = null, @JsonProperty("url") val url: String? = null, @JsonProperty("resolutions") val resolutions: String? = null)
        data class Captions(@JsonProperty("lan") val lan: String? = null, @JsonProperty("lanName") val lanName: String? = null, @JsonProperty("url") val url: String? = null)
    }
}

data class MediaDetail(@JsonProperty("data") val data: Data? = null) {
    data class Data(
        @JsonProperty("subject") val subject: Items? = null,
        @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(@JsonProperty("name") val name: String? = null, @JsonProperty("character") val character: String? = null, @JsonProperty("avatarUrl") val avatarUrl: String? = null)
        data class Resource(@JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf()) {
            data class Seasons(@JsonProperty("se") val se: Int? = null, @JsonProperty("maxEp") val maxEp: Int? = null, @JsonProperty("allEp") val allEp: String? = null)
        }
    }
}

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse? {
        val finalId = subjectId ?: id
        if (finalId.isNullOrEmpty()) return null
        val url = "${provider.mainUrl}/detail/${finalId}"
        return provider.newMovieSearchResponse(
            title ?: "Unknown",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue?.toString())
        }
    }
    data class Cover(@JsonProperty("url") val url: String? = null)
    data class Trailer(@JsonProperty("videoAddress") val videoAddress: VideoAddress? = null) {
        data class VideoAddress(@JsonProperty("url") val url: String? = null)
    }
}

// --- DATA CLASSES TMDB (Simple) ---
data class TmdbSearchResponse(
    val results: List<TmdbResult>?
)

data class TmdbResult(
    val id: Int,
    val title: String?,
    val name: String?, // TV Series uses 'name'
    val release_date: String?,
    val first_air_date: String? // TV Series
)

data class TmdbDetailResponse(
    val id: Int,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Double?,
    val genres: List<TmdbGenre>?,
    val credits: TmdbCredits?
)

data class TmdbGenre(val name: String?)
data class TmdbCredits(val cast: List<TmdbCast>?)
data class TmdbCast(val name: String?, val character: String?, val profile_path: String?)
