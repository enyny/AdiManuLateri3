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

    // Domain untuk Halaman Utama (Trending) ditemukan dari screenshot 1002113717.jpg
    private val trendingApi = "https://h5-api.aoneroom.com"
    
    // Domain untuk Detail dan Play ditemukan dari screenshot 1002113731.jpg & 1002113741.jpg
    private val contentApi = "https://filmboom.top"

    override val mainPage: List<MainPageData> = mainPageOf(
        "trending" to "Trending",
        "hottest" to "Hottest",
        "latest" to "Latest"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        // Menggunakan domain h5-api.aoneroom.com sesuai log network
        // URL: .../wefeed-h5api-bff/subject/trending?page=0&perPage=18
        val endpoint = if (request.data == "trending") "trending" else "search"
        val url = "$trendingApi/wefeed-h5api-bff/subject/trending?page=${page - 1}&perPage=18"
        
        val response = app.get(url).parsedSafe<MediaResponse>()
        
        val home = response?.data?.subjectList?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("No Data Found")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val body = mapOf(
            "keyword" to query,
            "page" to 0,
            "perPage" to 20
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        // Menggunakan contentApi karena search biasanya ada di domain utama aplikasi
        val searchUrl = "$contentApi/wefeed-h5-bff/web/subject/everyone-search"
        
        return app.post(searchUrl, requestBody = body).parsedSafe<MediaResponse>()?.data?.subjectList?.map {
            it.toSearchResponse(this)
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        // Mengambil ID dari URL dummy yang kita buat
        val id = url.substringAfter("id=")
        
        // Menggunakan domain filmboom.top untuk detail
        val detailUrl = "$contentApi/wefeed-h5-bff/web/subject/detail?subjectId=$id"
        val response = app.get(detailUrl).parsedSafe<DetailResponse>()?.data 
            ?: throw ErrorLoadingException("Failed to load details")

        val subject = response.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val description = subject?.description
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        
        // subjectType 1 = Movie, 2 = Series (Berdasarkan data JSON sebelumnya)
        val isMovie = subject?.subjectType == 1
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
        
        val rating = Score.from10(subject?.imdbRatingValue)
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val trailer = subject?.trailer?.videoAddress?.url

        val actors = response.stars?.mapNotNull { star ->
            Actor(star.name ?: return@mapNotNull null, star.avatarUrl)
        }

        val recommendations = emptyList<SearchResponse>()

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, LinkData(id, 0, 0).toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            val episodes = ArrayList<Episode>()
            
            // Mengelola Episode Serial TV
            response.resource?.seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                // Jika allEp null, buat range dari 1 sampai maxEp
                val epList = if (!season.allEp.isNullOrEmpty()) {
                    season.allEp.split(",").mapNotNull { it.toIntOrNull() }
                } else {
                    (1..(season.maxEp ?: 0)).toList()
                }

                epList.forEach { epNum ->
                    episodes.add(
                        newEpisode(LinkData(id, seasonNum, epNum).toJson()) {
                            this.season = seasonNum
                            this.episode = epNum
                            this.name = "Episode $epNum"
                            this.posterUrl = poster
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
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
        val loadData = parseJson<LinkData>(data)
        
        // Menggunakan domain filmboom.top untuk Play
        // URL: .../web/subject/play?subjectId=...
        val playUrl = "$contentApi/wefeed-h5-bff/web/subject/play?subjectId=${loadData.id}&se=${loadData.season}&ep=${loadData.episode}"
        
        val response = app.get(playUrl).parsedSafe<PlayResponse>()?.data
        
        // Parsing link stream dari JSON response
        response?.streams?.forEach { stream ->
            val qualityStr = stream.resolutions ?: ""
            val quality = getQualityFromName(qualityStr)
            
            callback.invoke(
                newExtractorLink(
                    this.name,
                    "MovieBox $qualityStr", // Menampilkan resolusi (360, 480, 1080)
                    stream.url ?: return@forEach,
                    Referer = "$contentApi/",
                    quality = quality
                )
            )
        }

        // Parsing subtitle jika tersedia
        response?.captions?.forEach { sub ->
            subtitleCallback.invoke(
                SubtitleFile(sub.lanName ?: "Unknown", sub.url ?: return@forEach)
            )
        }

        return true
    }
}

// --- DATA CLASSES (Disesuaikan dengan struktur JSON di screenshot) ---

data class LinkData(
    val id: String,
    val season: Int,
    val episode: Int
)

data class MediaResponse(
    @JsonProperty("data") val data: DataList? = null
) {
    data class DataList(
        @JsonProperty("subjectList") val subjectList: List<SubjectItem>? = null,
        @JsonProperty("items") val items: List<SubjectItem>? = null
    )
}

data class SubjectItem(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("imdbRatingValue") val rating: String? = null,
) {
    data class Cover(
        @JsonProperty("url") val url: String? = null
    )

    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val isMovie = subjectType == 1
        val id = subjectId ?: ""
        
        return provider.newMovieSearchResponse(
            title ?: "",
            "${provider.mainUrl}/detail?id=$id", 
            if (isMovie) TvType.Movie else TvType.TvSeries,
        ) {
            this.posterUrl = cover?.url
            this.plot = description
            this.score = Score.from10(rating)
        }
    }
}

data class DetailResponse(
    @JsonProperty("data") val data: DetailData? = null
) {
    data class DetailData(
        @JsonProperty("subject") val subject: SubjectDetail? = null,
        @JsonProperty("stars") val stars: List<Star>? = null,
        @JsonProperty("resource") val resource: Resource? = null
    )
}

data class SubjectDetail(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("cover") val cover: SubjectItem.Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null
) {
    data class Trailer(
        @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null
    ) {
        data class VideoAddress(
            @JsonProperty("url") val url: String? = null
        )
    }
}

data class Star(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("avatarUrl") val avatarUrl: String? = null
)

data class Resource(
    @JsonProperty("seasons") val seasons: List<Season>? = null
) {
    data class Season(
        @JsonProperty("se") val se: Int? = null,
        @JsonProperty("maxEp") val maxEp: Int? = null,
        @JsonProperty("allEp") val allEp: String? = null
    )
}

// Class khusus untuk menangani JSON Play dari screenshot 1002113743.jpg
data class PlayResponse(
    @JsonProperty("data") val data: PlayData? = null
) {
    data class PlayData(
        @JsonProperty("streams") val streams: List<Stream>? = null,
        @JsonProperty("captions") val captions: List<Caption>? = null
    ) {
        data class Stream(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolutions") val resolutions: String? = null, // "360", "1080", dll
            @JsonProperty("format") val format: String? = null
        )

        data class Caption(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("lanName") val lanName: String? = null
        )
    }
}
