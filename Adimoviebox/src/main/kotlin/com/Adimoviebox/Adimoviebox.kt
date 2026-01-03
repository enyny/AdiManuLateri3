package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
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
    
    // Identitas perangkat agar tidak dianggap bot
    private val deviceId = List(16) { Random.nextInt(0, 16).toString(16) }.joinToString("")
    private val userAgent = "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    private suspend fun getAuthToken(): String {
        return tokenMutex.withLock {
            if (!currentToken.isNullOrBlank()) return@withLock currentToken!!

            // Request login dengan body JSON agar server tidak menolak
            val res = app.post(
                "$searchApiUrl/user/anonymous-login",
                headers = mapOf(
                    "X-Request-Lang" to "en",
                    "User-Agent" to userAgent,
                    "Content-Type" to "application/json"
                ),
                data = mapOf("deviceId" to deviceId)
            ).parsedSafe<LoginResponse>()
            
            val token = res?.data?.token
            if (!token.isNullOrBlank()) {
                currentToken = "Bearer $token"
                return@withLock currentToken!!
            }
            throw ErrorLoadingException("Gagal mendapatkan akses server")
        }
    }

    private suspend fun getHeaders(isPlayDomain: Boolean = false): Map<String, String> {
        val target = if (isPlayDomain) "https://filmboom.top" else "https://moviebox.ph"
        return mapOf(
            "Authorization" to getAuthToken(),
            "X-Request-Lang" to "en", // Gunakan 'en' untuk kestabilan API
            "Origin" to target,
            "Referer" to "$target/",
            "User-Agent" to userAgent,
            "Platform" to "android"
        )
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "$searchApiUrl/subject/trending" to "Sedang Tren 🔥",
        "$searchApiUrl/subject/filter?channelId=1&area=Indonesia" to "Film Indonesia Lagi Ngetren",
        "$searchApiUrl/subject/filter?channelId=2&area=Indonesia" to "Drama Indonesia Terkini 💗",
        "$searchApiUrl/subject/filter?channelId=1004" to "Hot Short TV 🎬",
        "$searchApiUrl/subject/filter?channelId=2&area=South Korea" to "K-Drama Terbaru",
        "$searchApiUrl/subject/filter?channelId=1006" to "Masuk ke Dunia Anime 🌟",
        "$searchApiUrl/subject/filter?channelId=2&genre=Bromance" to "Bromance",
        "$searchApiUrl/subject/filter?channelId=1&area=USA" to "Impian Filem Hollywood"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("?")) "${request.data}&page=${page - 1}&perPage=18" 
                  else "${request.data}?page=${page - 1}&perPage=18"
        
        // Coba memuat data, jika gagal berikan fallback list kosong agar tidak crash
        val response = try {
            app.get(url, headers = getHeaders(), timeout = 20).parsedSafe<Media>()?.data
        } catch (e: Exception) { null }

        val items = (response?.items ?: response?.subjectList)?.mapNotNull {
            it.toSearchResponse(this)
        } ?: emptyList() // Balas dengan list kosong jika server sibuk agar UI tidak error
        
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get(
            "$searchApiUrl/subject/everyone-search",
            params = mapOf("keyword" to query),
            headers = getHeaders()
        ).parsedSafe<Media>()?.data
        return (res?.items ?: res?.subjectList)?.mapNotNull { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val detail = app.get("$playApiUrl/subject/detail?subjectId=$id", headers = getHeaders(true))
            .parsedSafe<MediaDetail>()?.data ?: throw ErrorLoadingException("Detail tidak ditemukan")
        
        val subject = detail.subject
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

            newTvSeriesLoadResponse(subject?.title ?: "", url, TvType.TvSeries, episodes) {
                this.posterUrl = subject?.cover?.url ?: subject?.coverVerticalUrl
                this.plot = subject?.description
            }
        } else {
            newMovieLoadResponse(subject?.title ?: "", url, TvType.Movie, LoadData(id, detailPath = subject?.detailPath).toJson()) {
                this.posterUrl = subject?.cover?.url ?: subject?.coverVerticalUrl
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
        val media = parseJson<LoadData>(data)
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
        
        app.get(
            "$playApiUrl/subject/caption?format=MP4&id=${res?.streams?.firstOrNull()?.id ?: ""}&subjectId=${media.id}",
            headers = getHeaders(true)
        ).parsedSafe<Media>()?.data?.captions?.map { sub ->
            subtitleCallback.invoke(newSubtitleFile(sub.lanName ?: "", sub.url ?: return@map))
        }

        return true
    }
}

// --- Data Classes ---
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

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null, 
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null, 
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("coverVerticalUrl") val coverVerticalUrl: String? = null,
    @JsonProperty("detailPath") val detailPath: String? = null
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse? {
        if (subjectId == null) return null
        return provider.newMovieSearchResponse(title ?: "", "${provider.mainUrl}/detail/$subjectId", if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) { 
            this.posterUrl = cover?.url ?: coverVerticalUrl
        }
    }
    data class Cover(@JsonProperty("url") val url: String? = null)
}

data class LoadData(val id: String?, val season: Int? = null, val episode: Int? = null, val detailPath: String? = null)
data class LoginResponse(@JsonProperty("data") val data: TokenData? = null)
data class TokenData(@JsonProperty("token") val token: String? = null)
data class MediaDetail(@JsonProperty("data") val data: Data? = null) {
    data class Data(@JsonProperty("subject") val subject: Items? = null, @JsonProperty("resource") val resource: Resource? = null) {
        data class Resource(@JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf())
        data class Seasons(@JsonProperty("se") val se: Int? = null, @JsonProperty("maxEp") val maxEp: Int? = null, @JsonProperty("allEp") val allEp: String? = null)
    }
}
