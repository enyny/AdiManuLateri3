package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    
    // Domain untuk Metadata & Search (Aoneroom)
    private val searchApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    
    // Domain untuk Video & Subtitle (Filmboom)
    private val playApiUrl = "https://filmboom.top/wefeed-h5-bff/web"
    
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id" // Paksa ke ID untuk konten lokal
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    private var currentToken: String? = null

    // Otomatisasi Token Bearer agar extension tidak mati
    private suspend fun getAuthToken(): String {
        currentToken?.let { return it }
        val res = app.post(
            "$searchApiUrl/user/anonymous-login",
            headers = mapOf("X-Request-Lang" to "id")
        ).parsedSafe<LoginResponse>()
        
        val token = res?.data?.token ?: ""
        currentToken = "Bearer $token"
        return currentToken!!
    }

    private suspend fun getHeaders(isPlayDomain: Boolean = false): Map<String, String> {
        return mapOf(
            "Authorization" to getAuthToken(),
            "X-Request-Lang" to "id",
            "Origin" to if (isPlayDomain) "https://filmboom.top" else "https://moviebox.ph",
            "Referer" to if (isPlayDomain) "https://filmboom.top/" else "https://moviebox.ph/"
        )
    }

    // Daftar baris kategori sesuai screenshot 1002112982.jpg
    override val mainPage: List<MainPageData> = mainPageOf(
        "$searchApiUrl/subject/trending" to "Sedang Tren 🔥",
        "$searchApiUrl/subject/filter?channelId=1&area=Indonesia" to "Film Indonesia Lagi Ngetren",
        "$searchApiUrl/subject/filter?channelId=2&area=Indonesia" to "Drama Indonesia Terkini 💗",
        "$searchApiUrl/subject/filter?channelId=1004&sort=1" to "Hot Short TV 🎬",
        "$searchApiUrl/subject/filter?channelId=2&area=Korea" to "K-Drama Terbaru",
        "$searchApiUrl/subject/filter?channelId=1006" to "Masuk ke Dunia Anime 🌟",
        "$searchApiUrl/subject/filter?channelId=2&genre=Bromance" to "Bromance",
        "$searchApiUrl/subject/filter?channelId=1&area=Indonesia&sort=1" to "Indonesian Killers",
        "$searchApiUrl/subject/filter?channelId=1&area=USA" to "Impian Filem Hollywood"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNum = page - 1
        val url = if (request.data.contains("?")) {
            "${request.data}&page=$pageNum&perPage=18"
        } else {
            "${request.data}?page=$pageNum&perPage=18"
        }
        
        // Request GET sesuai log devtools
        val home = app.get(url, headers = getHeaders()).parsedSafe<Media>()?.data?.items?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Kategori ${request.name} tidak dapat dimuat")
        
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get(
            "$searchApiUrl/subject/everyone-search",
            params = mapOf("keyword" to query),
            headers = getHeaders()
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        // Mengambil detail dari domain filmboom
        val detail = app.get("$playApiUrl/subject/detail?subjectId=$id", headers = getHeaders(true))
            .parsedSafe<MediaDetail>()?.data ?: throw ErrorLoadingException("Gagal memuat detail")
        
        val subject = detail.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = detail.resource?.seasons?.map { season ->
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
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(id, detailPath = subject?.detailPath).toJson()) {
                this.posterUrl = poster
                this.plot = subject?.description
                this.year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
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
        // Request streaming dari filmboom
        val res = app.get(
            "$playApiUrl/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detail_path=${media.detailPath}",
            headers = getHeaders(true)
        ).parsedSafe<Media>()?.data

        res?.streams?.map { source ->
            callback.invoke(
                newExtractorLink(this.name, this.name, source.url ?: return@map, INFER_TYPE) {
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }
        
        // Pemuatan Subtitle otomatis
        app.get(
            "$playApiUrl/subject/caption?format=MP4&id=${res?.streams?.firstOrNull()?.id ?: ""}&subjectId=${media.id}",
            headers = getHeaders(true)
        ).parsedSafe<Media>()?.data?.captions?.map { sub ->
            subtitleCallback.invoke(newSubtitleFile(sub.lanName ?: "", sub.url ?: return@map))
        }

        return true
    }
}

// --- Data Structures ---
data class LoadData(val id: String?, val season: Int? = null, val episode: Int? = null, val detailPath: String? = null)
data class LoginResponse(@JsonProperty("data") val data: TokenData? = null)
data class TokenData(@JsonProperty("token") val token: String? = null)

data class Media(@JsonProperty("data") val data: Data? = null) {
    data class Data(
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf()
    ) {
        data class Streams(@JsonProperty("id") val id: String? = null, @JsonProperty("url") val url: String? = null, @JsonProperty("resolutions") val resolutions: String? = null)
        data class Captions(@JsonProperty("lanName") val lanName: String? = null, @JsonProperty("url") val url: String? = null)
    }
}

data class MediaDetail(@JsonProperty("data") val data: Data? = null) {
    data class Data(@JsonProperty("subject") val subject: Items? = null, @JsonProperty("resource") val resource: Resource? = null) {
        data class Resource(@JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf())
        data class Seasons(@JsonProperty("se") val se: Int? = null, @JsonProperty("maxEp") val maxEp: Int? = null, @JsonProperty("allEp") val allEp: String? = null)
    }
}

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null, @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null, @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null, @JsonProperty("cover") val cover: Cover? = null, 
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null, @JsonProperty("detailPath") val detailPath: String? = null
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        return provider.newMovieSearchResponse(title ?: "", "${provider.mainUrl}/detail/$subjectId", if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) { this.posterUrl = cover?.url }
    }
    data class Cover(@JsonProperty("url") val url: String? = null)
}
