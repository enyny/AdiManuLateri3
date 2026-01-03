package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    
    // Domain navigasi dari Network Log
    private val searchApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    
    // Domain streaming dari Network Log
    private val playApiUrl = "https://filmboom.top/wefeed-h5-bff/web"
    
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id" // Disetel ke ID untuk kategori lokal
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    private var currentToken: String? = null

    // Login anonim otomatis untuk mendapatkan token Bearer
    private suspend fun getAuthToken(): String {
        currentToken?.let { return it }
        val res = app.post(
            "$searchApiUrl/user/anonymous-login",
            headers = mapOf("X-Request-Lang" to "en")
        ).parsedSafe<LoginResponse>()
        
        val token = res?.data?.token ?: ""
        currentToken = "Bearer $token"
        return currentToken!!
    }

    // Header dinamis menyesuaikan target domain
    private suspend fun getHeaders(isPlayDomain: Boolean = false): Map<String, String> {
        return mapOf(
            "Authorization" to getAuthToken(),
            "X-Request-Lang" to "id",
            "Origin" to if (isPlayDomain) "https://filmboom.top" else "https://moviebox.ph",
            "Referer" to if (isPlayDomain) "https://filmboom.top/" else "https://moviebox.ph/"
        )
    }

    // Daftar halaman utama berdasarkan tampilan terbaru
    override val mainPage: List<MainPageData> = mainPageOf(
        "$searchApiUrl/subject/trending" to "Sedang Tren 🔥",
        "$searchApiUrl/home?host=moviebox.ph" to "Pilihan Utama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("trending")) {
            "${request.data}?page=${page - 1}&perPage=18" 
        } else {
            request.data
        }
        
        // Menggunakan method GET sesuai temuan log
        val home = app.get(url, headers = getHeaders()).parsedSafe<Media>()?.data?.items?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat halaman utama")
        
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Endpoint pencarian global
        return app.get(
            "$searchApiUrl/subject/everyone-search",
            params = mapOf("keyword" to query),
            headers = getHeaders()
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        // Detail dimuat dari domain filmboom
        val detail = app.get("$playApiUrl/subject/detail?subjectId=$id", headers = getHeaders(true))
            .parsedSafe<MediaDetail>()?.data ?: throw ErrorLoadingException("Detail tidak tersedia")
        
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
                this.score = Score.from10(subject?.imdbRatingValue)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(id, detailPath = subject?.detailPath).toJson()) {
                this.posterUrl = poster
                this.plot = subject?.description
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
        
        // Mengambil link stream dari domain filmboom
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
        
        // Integrasi Subtitle dari API caption
        app.get(
            "$playApiUrl/subject/caption?format=MP4&id=${res?.streams?.firstOrNull()?.id ?: ""}&subjectId=${media.id}",
            headers = getHeaders(true)
        ).parsedSafe<Media>()?.data?.captions?.map { sub ->
            subtitleCallback.invoke(newSubtitleFile(sub.lanName ?: "", sub.url ?: return@map))
        }

        return true
    }
}

// --- Struktur Data ---
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
    @JsonProperty("subjectId") val subjectId: String? = null, 
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null, 
    @JsonProperty("description") val description: String? = null,
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
