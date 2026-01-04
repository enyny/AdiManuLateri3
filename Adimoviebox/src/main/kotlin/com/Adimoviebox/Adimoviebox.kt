package com.Adimoviebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.TimeZone

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiHost = "https://h5-api.aoneroom.com"
    
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    private val deviceTimezone: String 
        get() = TimeZone.getDefault().id

    // ⚠️ PERINGATAN: Ganti token ini jika film tetap tidak muncul (Data Null)
    private val commonHeaders: Map<String, String>
        get() = mapOf(
            "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiI0NjQyNzc1MTQyOTY1ODY5NjA5NiIsImV4cCI6MTc2NzUzMjI4MDY4MX0.0a21f9c317675348954000aefa9b4eaa", 
            "X-Client-Info" to "{\"timezone\":\"$deviceTimezone\"}",
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "X-Request-Lang" to "en"
        )

    override val mainPage: List<MainPageData> = mainPageOf(
        "$apiHost/wefeed-h5api-bff/home?host=moviebox.ph" to "Home",
        "$apiHost/wefeed-h5api-bff/subject/trending?page=0&perPage=18" to "Trending Now",
        "5,Korea,All" to "K-Drama",
        "2,Indonesia,All" to "Indo Film",
        "5,China,All" to "C-Drama",
        "5,All,Anime" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        try {
            if (request.data.startsWith("http")) {
                val response = app.get(request.data, headers = commonHeaders).text
                val json = parseJson<MediaResponse>(response)
                
                json.data?.operatingList?.filter { it.type == "SUBJECTS_MOVIE" || it.type == "BANNER" }?.forEach { op ->
                    op.subjects?.forEach { items.add(it.toSearchResponse(this)) }
                    op.banner?.items?.forEach { it.subject?.let { sub -> items.add(sub.toSearchResponse(this)) } }
                }
                json.data?.subjectList?.forEach { items.add(it.toSearchResponse(this)) }
            } else {
                val params = request.data.split(",")
                val body = mapOf(
                    "tabId" to params[0],
                    "page" to page.toString(),
                    "perPage" to "20",
                    "filterType" to mapOf("country" to params[1], "genre" to params[2], "sort" to "Hottest", "year" to "All").toJson()
                )
                // KOREKSI: Path filter tanpa "/web/"
                val response = app.post("$apiHost/wefeed-h5api-bff/subject/filter", headers = commonHeaders, json = body).parsedSafe<MediaResponse>()
                response?.data?.items?.forEach { items.add(it.toSearchResponse(this)) }
            }
        } catch (e: Exception) { }
        return newHomePageResponse(request.name, items.distinctBy { it.name })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body = mapOf("keyword" to query, "page" to "1", "perPage" to "20", "subjectType" to "0")
        // KOREKSI: Path search tanpa "/web/"
        return app.post("$apiHost/wefeed-h5api-bff/subject/search", headers = commonHeaders, json = body)
            .parsedSafe<MediaResponse>()?.data?.items?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        // KOREKSI KRITIS: Menghapus "/web/" dari path detail berdasarkan Image 1002113433.jpg
        val response = app.get("$apiHost/wefeed-h5api-bff/subject/detail?subjectId=$id", headers = commonHeaders)
        
        val res = response.parsedSafe<MediaDetailResponse>()?.data 
            ?: throw ErrorLoadingException("Data Null: Token Expired or Path Wrong")

        val subject = res.subject
        val isTv = subject?.subjectType == 2

        return if (isTv) {
            val episodes = res.resource?.seasons?.flatMap { season ->
                List(season.maxEp ?: 0) { i ->
                    newEpisode(LoadData(id, season.se, i + 1, subject?.detailPath).toJson()) {
                        this.season = season.se
                        this.episode = i + 1
                    }
                }
            } ?: emptyList()
            newTvSeriesLoadResponse(subject?.title ?: "No Title", url, TvType.TvSeries, episodes) {
                fillDetails(this, subject)
            }
        } else {
            newMovieLoadResponse(subject?.title ?: "No Title", url, TvType.Movie, LoadData(id, detailPath = subject?.detailPath).toJson()) {
                fillDetails(this, subject)
            }
        }
    }

    private fun fillDetails(container: LoadResponse, item: Items?) {
        container.posterUrl = item?.cover?.url ?: "" 
        container.plot = if (item?.description.isNullOrBlank()) "No description available." else item?.description
        container.year = item?.releaseDate?.substringBefore("-")?.toIntOrNull()
        container.score = Score.from10(item?.imdbRatingValue)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        // KOREKSI: Path play tanpa "/web/"
        val response = app.get("$apiHost/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}", headers = commonHeaders)
            .parsedSafe<MediaResponse>()?.data

        response?.streams?.forEach { stream ->
            callback.invoke(
                newExtractorLink(
                    this.name, this.name, stream.url ?: return@forEach, 
                    if (stream.format == "m3u8") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.quality = getQualityFromName(stream.resolutions)
                    this.referer = "$mainUrl/"
                }
            )
        }
        return true
    }
}

// --- Data Models ---
data class LoadData(val id: String?, val season: Int? = null, val episode: Int? = null, val detailPath: String?)
data class MediaResponse(val data: Data? = null) {
    data class Data(val operatingList: List<OperatingItem>? = null, val subjectList: List<Items>? = null, val items: List<Items>? = null, val streams: List<Stream>? = null)
}
data class OperatingItem(val type: String? = null, val subjects: List<Items>? = null, val banner: Banner? = null)
data class Banner(val items: List<BannerItem>? = null)
data class BannerItem(val subject: Items? = null)
data class Stream(val url: String?, val resolutions: String?, val format: String?)
data class MediaDetailResponse(val data: Data? = null) {
    data class Data(val subject: Items? = null, val resource: Resource? = null) {
        data class Resource(val seasons: List<Season>? = null)
        data class Season(val se: Int?, val maxEp: Int?)
    }
}
data class Items(val subjectId: String?, val subjectType: Int?, val title: String?, val description: String?, val releaseDate: String?, val cover: Cover?, val imdbRatingValue: String?, val detailPath: String?) {
    fun toSearchResponse(api: MainAPI): SearchResponse = api.newMovieSearchResponse(title ?: "Unknown", "${api.mainUrl}/detail/$subjectId", if (subjectType == 2) TvType.TvSeries else TvType.Movie, false) { this.posterUrl = cover?.url ?: "" }
    data class Cover(val url: String?)
}
