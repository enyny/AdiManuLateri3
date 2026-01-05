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

// ✅ Import wajib
import com.lagradost.cloudstream3.utils.INFER_TYPE 

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

    // Header lengkap hasil fetch log kamu
    private val commonHeaders = mapOf(
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0",
        "content-type" to "application/json",
        "sec-ch-ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Linux\"",
        "sec-fetch-dest" to "empty",
        "sec-fetch-mode" to "cors",
        "sec-fetch-site" to "cross-site",
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
        "x-request-lang" to "en",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/"
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

        val filmList = targetCategory?.subjects?.mapNotNull {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Kategori tidak ditemukan/Token Expired.")

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
        ).parsedSafe<Media>()?.data?.items?.mapNotNull { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian gagal.")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.trimEnd('/').substringAfterLast("/")
        
        val response = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$id",
            headers = commonHeaders
        ).parsedSafe<MediaDetail>()

        val document = response?.data ?: throw ErrorLoadingException("Gagal memuat data detail.")
        val subject = document.subject ?: throw ErrorLoadingException("Info film kosong.")
        
        val title = subject.title ?: "No Title"
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        val score = Score.from10(subject.imdbRatingValue) 
        
        val actors = document.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
            headers = commonHeaders
        ).parsedSafe<Media>()?.data?.items?.mapNotNull {
            it.toSearchResponse(this)
        }

        if (tvType == TvType.TvSeries) {
            val episodes = document.resource?.seasons?.flatMap { seasons ->
                val epList = if (seasons.allEp.isNullOrEmpty()) {
                    (1..(seasons.maxEp ?: 0)).toList()
                } else {
                    seasons.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                }

                epList.map { epNum ->
                    newEpisode(
                        LoadData(
                            id,
                            seasons.se,
                            epNum,
                            subject.detailPath
                        ).toJson()
                    ) {
                        this.season = seasons.se
                        this.episode = epNum
                    }
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
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
                addTrailer(trailer)
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
        
        if (media.detailPath.isNullOrEmpty()) return false

        val playReferer = "$playApiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&detailSe=&detailEp=&lang=en"

        val playHeaders = mapOf(
            "authority" to "filmboom.top",
            "accept" to "application/json",
            "origin" to "https://filmboom.top",
            "referer" to playReferer,
            "user-agent" to (commonHeaders["user-agent"] ?: ""),
            "x-client-info" to (commonHeaders["x-client-info"] ?: ""),
            "x-request-lang" to "en"
        )

        val targetUrl = "$playApiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detail_path=${media.detailPath}"

        val response = app.get(targetUrl, headers = playHeaders).parsedSafe<Media>()

        response?.data?.streams?.forEach { source ->
            val streamUrl = source.url ?: return@forEach
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    streamUrl,
                    INFER_TYPE
                ) {
                    this.referer = "$playApiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val firstStream = response?.data?.streams?.firstOrNull()
        val videoId = firstStream?.id
        val format = firstStream?.format

        if (videoId != null && format != null) {
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

} // ✅ INI KURUNG KURAWAL YANG TADI HILANG

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
    fun toSearchResponse(provider: Adimoviebox): SearchResponse? {
        val finalId = subjectId ?: id ?: return null
        val url = "${provider.mainUrl}/detail/${finalId}"

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue)
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
