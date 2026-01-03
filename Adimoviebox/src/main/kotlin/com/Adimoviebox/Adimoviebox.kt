package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val searchApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private val playApiUrl = "https://filmboom.top/wefeed-h5-bff/web"
    
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    private var currentToken: String? = null
    private val tokenMutex = Mutex() 
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
    private val deviceId = List(16) { Random.nextInt(0, 16).toString(16) }.joinToString("")

    private suspend fun getAuthToken(): String {
        return tokenMutex.withLock {
            if (!currentToken.isNullOrBlank()) return@withLock currentToken!!
            val res = app.post(
                "$searchApiUrl/user/anonymous-login",
                headers = mapOf("X-Request-Lang" to "id", "User-Agent" to userAgent),
                data = mapOf("host" to "moviebox.ph", "deviceId" to deviceId)
            ).parsedSafe<LoginResponse>()
            val token = res?.data?.token ?: ""
            currentToken = if (token.isNotEmpty()) "Bearer $token" else ""
            currentToken!!
        }
    }

    private suspend fun getHeaders(isPlayDomain: Boolean = false): Map<String, String> {
        val target = if (isPlayDomain) "https://filmboom.top" else "https://moviebox.ph"
        return mapOf(
            "Authorization" to getAuthToken(),
            "X-Request-Lang" to "id",
            "Platform" to "android",
            "Origin" to target,
            "Referer" to "$target/",
            "User-Agent" to userAgent
        )
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "$searchApiUrl/home?host=moviebox.ph" to "Sedang Tren 🔥", 
        "$searchApiUrl/subject/filter?channelId=1&area=Indonesia" to "Film Indonesia Lagi Ngetren",
        "$searchApiUrl/subject/filter?channelId=2&area=Indonesia" to "Drama Indonesia Terkini 💗",
        "$searchApiUrl/subject/filter?channelId=2&area=South Korea" to "K-Drama Terbaru",
        "$searchApiUrl/subject/filter?channelId=1006" to "Masuk ke Dunia Anime 🌟",
        "$searchApiUrl/subject/filter?channelId=1&area=USA" to "Impian Filem Hollywood"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        delay(Random.nextLong(700, 1800))
        val pageNum = page - 1
        val url = if (request.data.contains("?")) "${request.data}&page=$pageNum&perPage=18" 
                  else "${request.data}?page=$pageNum&perPage=18"
        
        val response = app.get(url, headers = getHeaders(), timeout = 45).parsedSafe<Media>()?.data
        val rawItems = response?.items ?: response?.subjectList ?: emptyList()
        val items = rawItems.mapNotNull { it.toSearchResponse(this) }
        
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get(
            "$searchApiUrl/subject/everyone-search",
            params = mapOf("keyword" to query),
            headers = getHeaders()
        ).parsedSafe<Media>()?.data
        val rawItems = res?.items ?: res?.subjectList ?: emptyList()
        return rawItems.mapNotNull { it.toSearchResponse(this) }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val detail = app.get("$playApiUrl/subject/detail?subjectId=$id", headers = getHeaders(true))
            .parsedSafe<MediaDetail>()?.data ?: throw ErrorLoadingException("Gagal muat detail")
        
        val subject = detail.subject
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val poster = subject?.findPoster()

        return if (tvType == TvType.TvSeries) {
            val episodes = detail.resource?.seasons?.map { season ->
                val epList = if (season.allEp.isNullOrEmpty()) (1..(season.maxEp ?: 1)).toList() 
                             else season.allEp!!.split(",").mapNotNull { it.toIntOrNull() }
                epList.map { ep ->
                    // Fix Syntax newEpisode & toJson
                    val episodeData = LoadData(id, season.se, ep, subject?.detailPath).toJson()
                    newEpisode(episodeData) {
                        this.season = season.se
                        this.episode = ep
                    }
                }
            }?.flatten() ?: emptyList()

            newTvSeriesLoadResponse(subject?.title ?: "", url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = subject?.description
            }
        } else {
            // Fix Syntax toJson
            val movieData = LoadData(id, detailPath = subject?.detailPath).toJson()
            newMovieLoadResponse(subject?.title ?: "", url, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.plot = subject?.description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Fix parseJson reference
        val media = parseJson<LoadData>(data)
        val res = app.get(
            "$playApiUrl/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detail_path=${media.detailPath ?: ""}",
            headers = getHeaders(true),
            timeout = 60
        ).parsedSafe<Media>()?.data

        val streams = res?.streams ?: return false
        streams.forEach { source ->
            callback.invoke(
                newExtractorLink(this.name, this.name, source.url ?: return@forEach, INFER_TYPE) {
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }
        
        app.get(
            "$playApiUrl/subject/caption?format=MP4&id=${streams.firstOrNull()?.id ?: ""}&subjectId=${media.id}",
            headers = getHeaders(true)
        ).parsedSafe<Media>()?.data?.captions?.forEach { sub ->
            subtitleCallback.invoke(newSubtitleFile(sub.lanName ?: "", sub.url ?: return@forEach))
        }
        return true
    }
}

// --- Data Classes ---

data class LoadData(
    val id: String?, 
    val season: Int? = null, 
    val episode: Int? = null, 
    val detailPath: String? = null
)

data class LoginResponse(@JsonProperty("data") val data: TokenData? = null)
data class TokenData(@JsonProperty("token") val token: String? = null)

data class Media(@JsonProperty("data") val data: Data? = null) {
    data class Data(
        @JsonProperty("items") val items: ArrayList<Items>? = null,
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = null,
        @JsonProperty("streams") val streams: ArrayList<Streams>? = null,
        @JsonProperty("captions") val captions: ArrayList<Captions>? = null
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
    @JsonProperty("subjectId") val subjectId: String? = null, 
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null, 
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("coverVerticalUrl") val coverVerticalUrl: String? = null,
    @JsonProperty("detailPath") val detailPath: String? = null
) {
    fun findPoster(): String? = cover?.url ?: coverVerticalUrl

    fun toSearchResponse(provider: Adimoviebox): SearchResponse? {
        val poster = findPoster() ?: return null
        if (subjectId == null) return null
        return provider.newMovieSearchResponse(title ?: "No Title", "${provider.mainUrl}/detail/$subjectId", if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) { 
            this.posterUrl = poster
        }
    }
    data class Cover(@JsonProperty("url") val url: String? = null)
}
