package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    // Update API URL berdasarkan hasil Network Log (Image 1 & 4)
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
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

    private var currentToken: String? = null

    // Fungsi otomatis untuk mengambil Token Bearer (Image 1)
    private suspend fun getAuthToken(): String {
        currentToken?.let { return it }

        // Endpoint login anonim standar untuk API jenis ini
        val res = app.post(
            "$apiUrl/user/anonymous-login",
            headers = mapOf("X-Request-Lang" to "en")
        ).parsedSafe<LoginResponse>()

        val token = res?.data?.token ?: ""
        currentToken = "Bearer $token"
        return currentToken!!
    }

    // Header dinamis untuk setiap request (Image 1 & 8)
    private suspend fun getHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to getAuthToken(),
            "X-Request-Lang" to "en",
            "Origin" to "https://moviebox.ph",
            "Referer" to "https://moviebox.ph/"
        )
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "$apiUrl/subject/trending" to "Trending Now",
        "$apiUrl/home?host=moviebox.ph" to "Home Selection"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val url = if (request.data.contains("trending")) {
            "${request.data}?page=${page - 1}&perPage=18"
        } else {
            request.data
        }

        val home = app.get(url, headers = getHeaders())
            .parsedSafe<Media>()?.data?.items?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("Data tidak ditemukan")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan endpoint everyone-search (Image 1)
        return app.get(
            "$apiUrl/subject/everyone-search",
            params = mapOf("keyword" to query),
            headers = getHeaders()
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val document = app.get(
            "$apiUrl/subject/detail?subjectId=$id", 
            headers = getHeaders()
        ).parsedSafe<MediaDetail>()?.data
        
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = document?.resource?.seasons?.map { season ->
                val epList = if (season.allEp.isNullOrEmpty()) (1..(season.maxEp ?: 1)) 
                             else season.allEp.split(",").map { it.toInt() }
                epList.map { ep ->
                    newEpisode(LoadData(id, season.se, ep, subject?.detailPath).toJson()) {
                        this.season = season.se
                        this.episode = ep
                    }
                }
            }?.flatten() ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = subject?.description
                this.year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
                this.score = Score.from10(subject?.imdbRatingValue)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(id, detailPath = subject?.detailPath).toJson()) {
                this.posterUrl = poster
                this.plot = subject?.description
                this.year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
                this.score = Score.from10(subject?.imdbRatingValue)
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
        
        val res = app.get(
            "$apiUrl/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            headers = getHeaders()
        ).parsedSafe<Media>()?.data

        res?.streams?.map { source ->
            callback.invoke(
                newExtractorLink(this.name, this.name, source.url ?: return@map, INFER_TYPE) {
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        return true
    }
}

// --- Data Classes ---
data class LoadData(val id: String?, val season: Int? = null, val episode: Int? = null, val detailPath: String? = null)

data class LoginResponse(@JsonProperty("data") val data: TokenData? = null)
data class TokenData(@JsonProperty("token") val token: String? = null)

data class Media(@JsonProperty("data") val data: Data? = null) {
    data class Data(
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf()
    ) {
        data class Streams(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolutions") val resolutions: String? = null
        )
    }
}

data class MediaDetail(@JsonProperty("data") val data: Data? = null) {
    data class Data(
        @JsonProperty("subject") val subject: Items? = null,
        @JsonProperty("resource") val resource: Resource? = null
    ) {
        data class Resource(@JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf())
        data class Seasons(
            @JsonProperty("se") val se: Int? = null,
            @JsonProperty("maxEp") val maxEp: Int? = null,
            @JsonProperty("allEp") val allEp: String? = null
        )
    }
}

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("detailPath") val detailPath: String? = null
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        return provider.newMovieSearchResponse(
            title ?: "", 
            "${provider.mainUrl}/detail/$subjectId", 
            if (subjectType == 1) TvType.Movie else TvType.TvSeries, 
            false
        ) { 
            this.posterUrl = cover?.url 
        }
    }
    data class Cover(@JsonProperty("url") val url: String? = null)
}
