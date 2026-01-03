package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    private suspend fun getAuthToken(): String {
        return tokenMutex.withLock {
            if (!currentToken.isNullOrBlank()) return@withLock currentToken!!
            val res = app.post(
                "$searchApiUrl/user/anonymous-login",
                headers = mapOf("X-Request-Lang" to "id", "User-Agent" to userAgent),
                data = mapOf("host" to "moviebox.ph")
            ).parsedSafe<LoginResponse>()
            val token = res?.data?.token ?: ""
            currentToken = if (token.isNotEmpty()) "Bearer $token" else ""
            currentToken!!
        }
    }

    private suspend fun getHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to getAuthToken(),
            "X-Request-Lang" to "id", // PENTING: Set 'id' biar dapet film Indonesia sesuai situs
            "User-Agent" to userAgent,
            "Platform" to "android"
        )
    }

    // ✅ DISINKRONKAN: Menggunakan endpoint /home agar sama dengan tampilan situs
    override val mainPage: List<MainPageData> = mainPageOf(
        "$searchApiUrl/home?host=moviebox.ph" to "Sedang Tren 🔥", 
        "$searchApiUrl/subject/filter?channelId=1&area=Indonesia" to "Film Indonesia Lagi Ngetren",
        "$searchApiUrl/subject/filter?channelId=2&area=Indonesia" to "Drama Indonesia Terkini 💗",
        "$searchApiUrl/subject/filter?channelId=2&area=South Korea" to "K-Drama Terbaru",
        "$searchApiUrl/subject/filter?channelId=1006" to "Masuk ke Dunia Anime 🌟"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        delay(Random.nextLong(500, 1000)) // Jeda lebih lama biar nggak 'Canceled'

        val pageNum = page - 1
        val url = if (request.data.contains("?")) "${request.data}&page=$pageNum&perPage=18" 
                  else "${request.data}?page=$pageNum&perPage=18"
        
        // Timeout dinaikkan ke 45 detik biar nggak kena 'JobCancellationException'
        val response = app.get(url, headers = getHeaders(), timeout = 45).parsedSafe<Media>()?.data
        
        // Filter film yang nggak punya ID atau Poster biar nggak error 'NullRequest'
        val items = (response?.items ?: response?.subjectList)?.mapNotNull {
            if (it.subjectId == null || (it.cover?.url == null && it.coverVerticalUrl == null)) null 
            else it.toSearchResponse(this)
        } ?: emptyList()
        
        return newHomePageResponse(request.name, items)
    }

    // ... (fungsi search, load, loadLinks tetap sama dengan Versi 10) ...
}

// Tambahkan pengecekan null di Items class
data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null, 
    @JsonProperty("title") val title: String? = null, 
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("coverVerticalUrl") val coverVerticalUrl: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("detailPath") val detailPath: String? = null
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        return provider.newMovieSearchResponse(
            title ?: "No Title", 
            "${provider.mainUrl}/detail/$subjectId", 
            if (subjectType == 1) TvType.Movie else TvType.TvSeries, 
            false
        ) { 
            this.posterUrl = cover?.url ?: coverVerticalUrl // Fallback poster
        }
    }
    data class Cover(@JsonProperty("url") val url: String? = null)
}

// ... (data class lainnya tetap sama) ...
