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

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://h5-api.aoneroom.com"
    private val playApiUrl = "https://filmboom.top"
    
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
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "Trending🔥" to "Trending🔥",
        "Trending Indonesian Movies" to "Trending Indonesian Movies",
        "Trending Indonesian Drama💗" to "Trending Indonesian Drama💗",
        "🔥Hot Short TV" to "🔥Hot Short TV",
        "K-Drama: New Release" to "K-Drama: New Release",
        "Into Animeverse🌟" to "Into Animeverse🌟",
        "👨‍❤️‍👨 Bromance" to "👨‍❤️‍👨 Bromance",
        "Indonesian Killers" to "Indonesian Killers",
        "Upcoming Calendar" to "Upcoming Calendar",
        "Western TV" to "Western TV",
        "Keluargaku yang Lucu 🏠" to "Keluargaku yang Lucu 🏠",
        "Hollywood Movies" to "Hollywood Movies",
        "We Won’t Be Eaten by the Rich!" to "We Won’t Be Eaten by the Rich!",
        "Cute World of Animals" to "Cute World of Animals",
        "C-Drama" to "C-Drama",
        "Run!! 🩸Escape Death!" to "Run!! 🩸Escape Death!",
        "No Regrets for Loving You" to "No Regrets for Loving You",
        "Must Watch Indo Dubbed" to "Must Watch Indo Dubbed",
        "Midnight Horror" to "Midnight Horror",
        "HA！Nobody Can Defeat Me" to "HA！Nobody Can Defeat Me",
        "🎮 Cyberpunk World" to "🎮 Cyberpunk World",
        "Animated Flim" to "Animated Flim",
        "Awas! Monster & Titan" to "Awas! Monster & Titan",
        "Tredning Thai-Drama" to "Tredning Thai-Drama",
        "👰Fake Marriage" to "👰Fake Marriage"
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

        val filmList = targetCategory?.subjects?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Kategori tidak ditemukan atau kosong")

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
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Search failed")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        val document = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$id",
            headers = commonHeaders
        ).parsedSafe<MediaDetail>()?.data

        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString()) 
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations =
            app.get(
                "$apiUrl/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
                headers = commonHeaders
            ).parsedSafe<Media>()?.data?.items?.map {
                    it.toSearchResponse(this)
                }

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id,
                                seasons.se,
                                episode,
                                subject?.detailPath
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = parseJson<LoadData>(data)
        
        // Referer Khusus Play
        val playReferer = "$playApiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&detailSe=&detailEp=&lang=en"

        val playHeaders = mapOf(
            "authority" to "filmboom.top",
            "accept" to "application/json",
            "origin" to "https://filmboom.top",
            "referer" to playReferer,
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
            "x-request-lang" to "en",
            "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0"
        )

        // Path untuk Play (wefeed-h5-bff)
        val targetUrl = "$playApiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detail_path=${media.detailPath}"

        val response = app.get(
            targetUrl,
            headers = playHeaders
        ).parsedSafe<Media>()

        response?.data?.streams?.forEach { source ->
            callback.invoke(
                // PERBAIKAN: Menggunakan Positional Arguments murni agar aman dari error "No Parameter Found"
                // Urutan standar: source, name, url, referer, quality, isM3u8
                newExtractorLink(
                    this.name,                          
                    "Server ${source.resolutions}p",    
                    source.url ?: return@forEach,
                    "$playApiUrl/", // Referer
                    getQualityFromName(source.resolutions), // Quality
                    false // isM3u8 (false karena ini MP4)
                )
            )
        }

        // Ambil Subtitle
        val videoId = response?.data?.streams?.firstOrNull()?.id
        val format = response?.data?.streams?.firstOrNull()?.format

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
