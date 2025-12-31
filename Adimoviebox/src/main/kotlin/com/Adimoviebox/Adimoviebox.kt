package com.Adimoviebox

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    // URL API Baru
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff" 
    
    // Token User (Valid sampai Maret 2026)
    private val authToken = "Bearer EyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjUwOTk0MzcyMzg5ODM4NDA4MjQsImF0cCI6MywiZXh0IjoiMTc2NzIwMzA0MCIsImV4cCI6MTc3NDk3OTA0MCwiaWF0IjoxNzY3MjAyNzQwfQ.mxTceq2bBWYz-zuKI2sSPkZByjLF_H_LTeUjGffCL1k"

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

    private fun getApiHeaders(): Map<String, String> {
        return mapOf(
            "authority" to "h5-api.aoneroom.com",
            "accept" to "application/json",
            "accept-language" to "en-US,en;q=0.9",
            "origin" to mainUrl,
            "referer" to "$mainUrl/",
            "authorization" to authToken,
            "x-request-lang" to "en",
            "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
            "user-agent" to "Mozilla/5.0 (Linux; Android 13; CPH2235) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36"
        )
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "trending" to "Trending Now",
        "everyone-search" to "Recommendation",
        "movies" to "Movies"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        // Logika Endpoint:
        // Jika request adalah 'trending', pakai endpoint trending.
        // Sisanya pakai 'everyone-search' yang lebih aman untuk list umum.
        val endpoint = if(request.data == "trending") "trending" else "everyone-search"
        val pg = page - 1 
        
        // Parameter URL disesuaikan. 'everyone-search' mungkin tidak butuh keyword.
        val url = if (endpoint == "trending") {
            "$apiUrl/subject/trending?page=$pg&perPage=20"
        } else {
            "$apiUrl/subject/everyone-search?page=$pg&perPage=20"
        }

        // DEBUGGING: Mencetak respon asli ke Logcat
        val responseText = app.get(url, headers = getApiHeaders()).text
        Log.d("DEBUG_ADI", "MainPage URL: $url")
        // Log.d("DEBUG_ADI", "Response: $responseText") // Uncomment jika ingin lihat isi JSON penuh

        // Parsing manual dengan try-catch agar tidak crash
        val mediaData = try {
            parseJson<Media>(responseText)
        } catch (e: Exception) {
            Log.e("DEBUG_ADI", "JSON Parse Error: ${e.message}")
            null
        }

        // Fallback: Cek 'items', jika kosong cek 'subjectList'
        val items = mediaData?.data?.items 
            ?: mediaData?.data?.subjectList 
            ?: emptyList()

        if (items.isEmpty()) throw ErrorLoadingException("No Data Found. Cek Logcat 'DEBUG_ADI'")

        val home = items.map {
            it.toSearchResponse(this)
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search?keyword=$query&page=0&perPage=20"
        
        val response = app.get(url, headers = getApiHeaders()).parsedSafe<Media>()
        
        // Fallback cek items atau subjectList
        val items = response?.data?.items ?: response?.data?.subjectList

        return items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Search failed")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val detailUrl = "$apiUrl/subject/detail?subjectId=$id"
        
        val document = app.get(detailUrl, headers = getApiHeaders())
            .parsedSafe<MediaDetail>()?.data
            
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

        val recommendations =
            app.get("$apiUrl/subject/detail-rec?subjectId=$id&page=0&perPage=12", headers = getApiHeaders())
                .parsedSafe<Media>()?.data?.items?.map {
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
        
        val playUrl = "$apiUrl/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}"

        val streams = app.get(
            playUrl,
            headers = getApiHeaders()
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams?.first()?.id
        val format = streams?.first()?.format

        app.get(
            "$apiUrl/subject/caption?format=$format&id=$id&subjectId=${media.id}",
            headers = getApiHeaders()
        ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.lanName ?: "",
                    subtitle.url ?: return@map
                )
            )
        }

        return true
    }
}

// --- Data Classes Update ---
// Menambahkan subjectList kembali untuk jaga-jaga

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
        // Kita dukung keduanya: items DAN subjectList
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        
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
