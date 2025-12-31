package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Adimoviebox : MainAPI() {
    // URL Web Utama (Referensi)
    override var mainUrl = "https://moviebox.ph" 
    
    // URL API Baru (Sesuai Screenshot h5-api.aoneroom.com)
    private val apiUrl = "https://h5-api.aoneroom.com"
    
    // Path Baru (Menghilangkan '/web/' dan menambahkan 'api')
    private val apiPrefix = "/wefeed-h5api-bff"

    // Token yang diambil dari screenshot headers yang kamu kirim
    private val authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjM0ODMzMzc2NjUwODQyOTQ5MzYsImF0IjoxNzY3MTg5MDA5LCJleHAiOjE3NjcxOTAwNjksImlzcyI6Imhpc2F2YW5hIn0.TZ9mWf4ePL7AyAvPfSaaTS6UAr6v9wiIUvwRyr2ikGA"

    override val instantLinkLoading = true
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // Header wajib untuk API baru agar tidak NO DATA FOUND
    private fun getApiHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer $authToken",
            "X-Client-Info" to """{"timezone":"Asia/Jayapura"}""", // Disesuaikan dengan screenshot kamu
            "X-Request-Lang" to "en",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/"
        )
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "1,ForYou" to "Movie ForYou",
        "1,Hottest" to "Movie Hottest",
        "1,Latest" to "Movie Latest",
        "1,Rating" to "Movie Rating",
        "2,ForYou" to "TVShow ForYou",
        "2,Hottest" to "TVShow Hottest",
        "2,Latest" to "TVShow Latest",
        "2,Rating" to "TVShow Rating",
        "1006,ForYou" to "Animation ForYou",
        "1006,Hottest" to "Animation Hottest",
        "1006,Latest" to "Animation Latest",
        "1006,Rating" to "Animation Rating",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val params = request.data.split(",")
        val body = mapOf(
            "channelId" to params.first(),
            "page" to page,
            "perPage" to "24",
            "sort" to params.last()
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        // Menggunakan apiUrl baru dan apiPrefix baru, serta headers
        // Path disesuaikan dengan struktur API baru
        val home = app.post(
            "$apiUrl$apiPrefix/web/filter", 
            requestBody = body,
            headers = getApiHeaders()
        ).parsedSafe<Media>()?.data?.items?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("No Data Found on Home")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan endpoint search dengan headers autentikasi
        return app.post(
            "$apiUrl$apiPrefix/subject/search", 
            headers = getApiHeaders(),
            requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Search failed")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        // Mengambil detail menggunakan endpoint API baru
        val document = app.get(
            "$apiUrl$apiPrefix/subject/detail?subjectId=$id",
            headers = getApiHeaders()
        ).parsedSafe<MediaDetail>()?.data
        
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString()) 
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        // Rekomendasi (Menggunakan endpoint detail-rec dari screenshot)
        val recommendations =
            app.get(
                "$apiUrl$apiPrefix/subject/detail-rec?subjectId=$id&page=1&perPage=12",
                headers = getApiHeaders()
            ).parsedSafe<Media>()?.data?.items?.map {
                it.toSearchResponse(this)
            }

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id,
                                seasons.se,
                                episode,
                                subject?.detailPath
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
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
        // Referer disesuaikan agar tidak diblokir
        val referer = "$mainUrl/"

        val streams = app.get(
            "$apiUrl$apiPrefix/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            headers = getApiHeaders() // WAJIB: Header Auth
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = referer
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format

        if (id != null && format != null) {
            app.get(
                "$apiUrl$apiPrefix/subject/caption?format=$format&id=$id&subjectId=${media.id}",
                headers = getApiHeaders()
            ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        subtitle.lanName ?: "",
                        subtitle.url ?: return@map
                    )
                )
            }
        }

        return true
    }
}

// --- Data Classes TETAP SAMA ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
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
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${subjectId}"

        return provider.newMovieSearchResponse(
            title ?: "",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue?.toString())
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
