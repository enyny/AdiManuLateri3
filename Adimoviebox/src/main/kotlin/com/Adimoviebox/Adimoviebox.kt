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

// ✅ PENTING: Import ini wajib ada agar INFER_TYPE dikenali
import com.lagradost.cloudstream3.utils.INFER_TYPE 

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://h5-api.aoneroom.com"
    private val playApiUrl = "https://filmboom.top"
    
    // TAG untuk pencarian di Logcat. Cari kata: "AdimovieboxDebug"
    private val DEBUG_TAG = "AdimovieboxDebug"

    override val instantLinkLoading = true
    override var name = "Adimoviebox"
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
        // HATI-HATI: Token statis ini mungkin kadaluwarsa.
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0"
    )

    // Fungsi bantuan untuk logging aman (System.out)
    private fun debugLog(message: String) {
        println("$DEBUG_TAG: $message")
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "Trending🔥" to "Trending🔥",
        "Trending Indonesian Movies" to "Trending Indonesian Movies",
        "Trending Indonesian Drama💗" to "Trending Indonesian Drama💗",
        "🔥Hot Short TV" to "🔥Hot Short TV",
        "K-Drama: New Release" to "K-Drama: New Release",
        "Into Animeverse🌟" to "Into Animeverse🌟",
        "Hollywood Movies" to "Hollywood Movies",
        "C-Drama" to "C-Drama",
        "Must Watch Indo Dubbed" to "Must Watch Indo Dubbed",
        "Animated Flim" to "Animated Flim"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val url = "$apiUrl/wefeed-h5api-bff/home?host=moviebox.ph"
        debugLog("=== GET MAIN PAGE ===")
        debugLog("Requesting: ${request.name} from URL: $url")

        try {
            val responseText = app.get(url, headers = commonHeaders).text
            // Log 500 karakter pertama untuk memastikan data masuk
            debugLog("Raw Response (Partial): ${responseText.take(500)}")

            val response = parseJson<HomeResponse>(responseText)
            
            val targetCategory = response.data?.operatingList?.find { 
                it.title?.trim() == request.name.trim() 
            }

            if (targetCategory == null) {
                debugLog("[ERROR] Category '${request.name}' NOT FOUND.")
                val availableTitles = response.data?.operatingList?.map { it.title }?.joinToString()
                debugLog("Available categories: $availableTitles")
                throw ErrorLoadingException("Kategori ${request.name} tidak ditemukan")
            }

            val filmList = targetCategory.subjects?.map {
                it.toSearchResponse(this)
            } ?: emptyList()

            debugLog("Found ${filmList.size} items for category ${request.name}")
            
            return newHomePageResponse(request.name, filmList)

        } catch (e: Exception) {
            debugLog("[CRITICAL ERROR] getMainPage: ${e.message}")
            e.printStackTrace()
            throw ErrorLoadingException("Error loading Main Page: ${e.message}")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/wefeed-h5api-bff/subject/search-suggest"
        debugLog("=== SEARCH ===")
        debugLog("Query: $query -> URL: $url")

        try {
            val rawJson = app.post(
                url,
                requestBody = mapOf(
                    "keyword" to query,
                    "perPage" to 20
                ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()),
                headers = commonHeaders
            ).text

            debugLog("Search Raw Response: $rawJson")

            val parsed = parseJson<Media>(rawJson)
            val items = parsed.data?.items?.map { it.toSearchResponse(this) }
            
            debugLog("Search result count: ${items?.size ?: 0}")
            
            return items ?: throw ErrorLoadingException("Search failed or returned no results.")
        } catch (e: Exception) {
            debugLog("[ERROR] Search Error: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        debugLog("=== LOAD ===")
        debugLog("Loading URL: $url")
        val id = url.substringAfterLast("/")
        debugLog("Extracted ID: $id")

        try {
            val detailUrl = "$apiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$id"
            val rawResponse = app.get(detailUrl, headers = commonHeaders).text
            debugLog("Detail Raw Response (Partial): ${rawResponse.take(500)}")

            val document = parseJson<MediaDetail>(rawResponse).data
            val subject = document?.subject

            if (subject == null) {
                debugLog("[ERROR] Subject data is NULL. Parsing failed.")
                throw ErrorLoadingException("Failed to parse movie details")
            }

            val title = subject.title ?: ""
            debugLog("Title found: $title")
            
            val poster = subject.cover?.url
            debugLog("Poster URL found: $poster") // Cek apakah poster null di sini
            
            val tags = subject.genre?.split(",")?.map { it.trim() }
            val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
            val tvType = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie
            val description = subject.description
            val trailer = subject.trailer?.videoAddress?.url
            val score = Score.from10(subject.imdbRatingValue?.toString()) 
            
            val actors = document.stars?.mapNotNull { cast ->
                ActorData(
                    Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                    roleString = cast.character
                )
            }?.distinctBy { it.actor }

            val recommendations = try {
                app.get(
                    "$apiUrl/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
                    headers = commonHeaders
                ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            } catch (e: Exception) {
                debugLog("[WARN] Failed to load recommendations: ${e.message}")
                null
            }

            if (tvType == TvType.TvSeries) {
                debugLog("Processing as TV Series")
                val episode = document.resource?.seasons?.map { seasons ->
                    (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 0)) else seasons.allEp.split(",")
                        .map { it.toInt() })
                        .map { episode ->
                            newEpisode(
                                LoadData(
                                    id,
                                    seasons.se,
                                    episode,
                                    subject.detailPath
                                ).toJson()
                            ) {
                                this.season = seasons.se
                                this.episode = episode
                            }
                        }
                }?.flatten() ?: emptyList()
                
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = score
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer, addRaw = true)
                }
            } else {
                debugLog("Processing as Movie")
                return newMovieLoadResponse(
                    title,
                    url,
                    TvType.Movie,
                    LoadData(id, detailPath = subject.detailPath).toJson()
                ) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = score
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer, addRaw = true)
                }
            }
        } catch (e: Exception) {
            debugLog("[CRITICAL] Error in LOAD: ${e.message}")
            e.printStackTrace()
            throw ErrorLoadingException("Load Error: ${e.message}")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        debugLog("=== LOAD LINKS ===")
        debugLog("Input Data: $data")

        try {
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
            
            debugLog("Fetching Stream URL: $targetUrl")
            
            val rawResponse = app.get(targetUrl, headers = playHeaders).text
            debugLog("Stream Raw Response: $rawResponse")

            val response = parseJson<Media>(rawResponse)
            val streams = response.data?.streams

            if (streams.isNullOrEmpty()) {
                debugLog("[ERROR] No streams found in response! Check Authorization or Headers.")
                return false
            }

            debugLog("Found ${streams.size} streams. Processing...")

            streams.forEach { source ->
                debugLog("Source: ${source.resolutions}, URL: ${source.url}")
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        source.url ?: return@forEach,
                        INFER_TYPE
                    ) {
                        this.referer = "$playApiUrl/"
                        this.quality = getQualityFromName(source.resolutions)
                    }
                )
            }

            val videoId = streams.firstOrNull()?.id
            val format = streams.firstOrNull()?.format

            if (videoId != null) {
                debugLog("Fetching captions for VideoID: $videoId")
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
        } catch (e: Exception) {
            debugLog("[ERROR] Error in LoadLinks: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}

// --- Data Classes ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
)

data class HomeResponse(
    @JsonProperty("data") val data: HomeData? = null
)

data class HomeData(
    @JsonProperty("operatingList") val operatingList: ArrayList<HomeModule>? = arrayListOf()
)

data class HomeModule(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("subjects") val subjects: ArrayList<Items>? = arrayListOf()
)

data class Media(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @JsonProperty("id") val id: String? = null,
            @JsonProperty("format") val format: String? = null,
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolutions") val resolutions: String? = null,
        )

        data class Captions(
            @JsonProperty("lan") val lan: String? = null,
            @JsonProperty("lanName") val lanName: String? = null,
            @JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subject") val subject: Items? = null,
        @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @JsonProperty("se") val se: Int? = null,
                @JsonProperty("maxEp") val maxEp: Int? = null,
                @JsonProperty("allEp") val allEp: String? = null,
            )
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
    @JsonProperty("duration") val duration: Long? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("countryName") val countryName: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val finalId = subjectId ?: id ?: ""
        val url = "${provider.mainUrl}/detail/${finalId}"
        
        // Debugging poster di sini tidak ideal karena akan spam log,
        // tapi kita pastikan cover?.url digunakan.
        
        return provider.newMovieSearchResponse(
            title ?: "",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue?.toString())
        }
    }

    data class Cover(
        @JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
