package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

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

    // Header Wajib berdasarkan analisis Network Tab
    private val commonHeaders = mapOf(
        "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", // PASTIKAN TOKEN INI VALID/TERBARU
        "X-Client-Info" to "{\"timezone\":\"Asia/Jayapura\"}",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "X-Request-Lang" to "en"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "$apiHost/wefeed-h5api-bff/home?host=moviebox.ph" to "Home",
        "$apiHost/wefeed-h5api-bff/subject/trending?page=0&perPage=18" to "Trending Now"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(request.data, headers = commonHeaders).text
        val json = parseJson<MediaResponse>(response)
        val items = mutableListOf<SearchResponse>()

        // Parsing dari Operating List (Beranda/Banner)
        json.data?.operatingList?.forEach { op ->
            op.subjects?.forEach { items.add(it.toSearchResponse(this)) }
            op.banner?.items?.forEach { it.subject?.let { sub -> items.add(sub.toSearchResponse(this)) } }
        }

        // Parsing dari Subject List (Halaman Trending)
        json.data?.subjectList?.forEach { items.add(it.toSearchResponse(this)) }

        return newHomePageResponse(request.name, items.distinctBy { it.name })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan endpoint pencarian asli (POST) untuk menghindari error 405
        val body = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "20",
            "subjectType" to "0"
        )
        return app.post(
            "$apiHost/wefeed-h5api-bff/subject/search",
            headers = commonHeaders,
            json = body
        ).parsedSafe<MediaResponse>()?.data?.items?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val res = app.get(
            "$apiHost/wefeed-h5api-bff/web/subject/detail?subjectId=$id",
            headers = commonHeaders
        ).parsedSafe<MediaDetailResponse>()?.data ?: throw ErrorLoadingException("Data Detail Null")

        val subject = res.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val isTv = subject?.subjectType == 2
        val rating = subject?.imdbRatingValue

        val recommendations = app.get(
            "$apiHost/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
            headers = commonHeaders
        ).parsedSafe<MediaResponse>()?.data?.items?.map { it.toSearchResponse(this) }

        return if (isTv) {
            val episodes = res.resource?.seasons?.flatMap { season ->
                List(season.maxEp ?: 0) { i ->
                    newEpisode(LoadData(id, season.se, i + 1, subject?.detailPath).toJson()) {
                        this.season = season.se
                        this.episode = i + 1
                    }
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = subject?.description
                this.year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
                this.score = Score.from10(rating)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(id, detailPath = subject?.detailPath).toJson()) {
                this.posterUrl = poster
                this.plot = subject?.description
                this.year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
                this.score = Score.from10(rating)
                this.recommendations = recommendations
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
        
        // Memanggil endpoint play untuk mendapatkan URL Streaming asli
        val response = app.get(
            "$apiHost/wefeed-h5api-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            headers = commonHeaders
        ).parsedSafe<MediaResponse>()?.data

        response?.streams?.forEach { stream ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    stream.url ?: return@forEach,
                    "$mainUrl/",
                    getQualityFromName(stream.resolutions),
                    isM3u8 = stream.format == "m3u8"
                )
            )
        }
        return true
    }
}

// --- Data Models (Disesuaikan dengan Hirarki JSON) ---

data class LoadData(val id: String?, val season: Int? = null, val episode: Int? = null, val detailPath: String?)

data class MediaResponse(val data: Data? = null) {
    data class Data(
        val operatingList: List<OperatingItem>? = null,
        val subjectList: List<Items>? = null,
        val items: List<Items>? = null,
        val everyoneSearch: List<Items>? = null,
        val streams: List<Stream>? = null
    )
}

data class MediaDetailResponse(val data: Data? = null) {
    data class Data(
        val subject: Items? = null,
        val resource: Resource? = null
    ) {
        data class Resource(val seasons: List<Season>? = null)
        data class Season(val se: Int?, val maxEp: Int?)
    }
}

data class OperatingItem(val subjects: List<Items>? = null, val banner: Banner? = null)
data class Banner(val items: List<BannerItem>? = null)
data class BannerItem(val subject: Items? = null)

data class Stream(val url: String?, val resolutions: String?, val format: String?)

data class Items(
    val subjectId: String?,
    val subjectType: Int?,
    val title: String?,
    val description: String?,
    val releaseDate: String?,
    val cover: Cover?,
    val imdbRatingValue: String?,
    val detailPath: String?,
    val genre: String?
) {
    fun toSearchResponse(api: MainAPI): SearchResponse {
        return api.newMovieSearchResponse(
            title ?: "",
            "${api.mainUrl}/detail/$subjectId",
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
            this.quality = SearchQuality.HD
        }
    }
    data class Cover(val url: String?)
}
