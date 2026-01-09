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
    // --- KONFIGURASI UTAMA ---
    override var mainUrl = "https://moviebox.ph"
    
    // API LAMA (Stabil untuk Search, Detail, & Play - Filmboom)
    private val apiUrl = "https://filmboom.top" 
    
    // API BARU (Untuk Home Page Layout - Aoneroom)
    private val homeApiUrl = "https://h5-api.aoneroom.com"

    override val instantLinkLoading = true
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

    // Headers Wajib untuk API Baru (Sesuai cURL /home)
    private val homeHeaders = mapOf(
        "Authority" to "h5-api.aoneroom.com",
        "Accept" to "application/json",
        "Origin" to "https://moviebox.ph",
        "Referer" to "https://moviebox.ph/",
        "x-request-lang" to "en"
    )

    // DAFTAR KATEGORI
    // Kunci (Kiri) harus COCOK dengan Judul Section dari API /home
    // Kanan: Judul Tampilan di Cloudstream
    override val mainPage: List<MainPageData> = mainPageOf(
        "Trending" to "Trending🔥",
        "Trending Indonesian Movies" to "Trending Indonesian Movies",
        "Trending Indonesian Drama" to "Trending Indonesian Drama💗",
        "Hot Short TV" to "🔥Hot Short TV",
        "K-Drama: New Release" to "K-Drama: New Release",
        "Into Animeverse" to "Into Animeverse🌟",
        "Bromance" to "👨‍❤️‍👨 Bromance",
        "Indonesian Killers" to "Indonesian Killers",
        "Upcoming Calendar" to "Upcoming Calendar",
        "Western TV" to "Western TV",
        "Keluargaku yang Lucu" to "Keluargaku yang Lucu 🏠",
        "Hollywood Movies" to "Hollywood Movies",
        "We Won't Be Eaten by the Rich!" to "We Won’t Be Eaten by the Rich!",
        "Cute World of Animals" to "Cute World of Animals",
        "C-Drama" to "C-Drama",
        "Run!! 🔥Escape Death!" to "Run!! 🩸Escape Death!", // Sesuaikan sedikit teksnya agar match
        "No Regrets for Loving You" to "No Regrets for Loving You",
        "Must Watch Indo Dubbed" to "Must Watch Indo Dubbed",
        "Midnight Horror" to "Midnight Horror",
        "HA! Nobody Can Defeat Me" to "HA！Nobody Can Defeat Me",
        "Cyberpunk World" to "🎮 Cyberpunk World",
        "Animated Flim" to "Animated Flim",
        "Awas! Monster & Titan" to "Awas! Monster & Titan",
        "Tredning Thai-Drama" to "Tredning Thai-Drama",
        "Fake Marriage" to "👰Fake Marriage"
    )

    // Cache untuk menyimpan struktur Home agar tidak request berulang kali
    private var homeLayoutCache: List<Items> = emptyList()

    // Fungsi fetch Home menggunakan endpoint /home?host=moviebox.ph
    private suspend fun fetchHomeLayout(): List<Items> {
        if (homeLayoutCache.isNotEmpty()) return homeLayoutCache

        return try {
            val response = app.get(
                "$homeApiUrl/wefeed-h5api-bff/home?host=moviebox.ph",
                headers = homeHeaders
            ).parsedSafe<HomeMedia>()
            
            // Di endpoint /home, datanya ada di dalam `data.items` (bukan subjectList)
            // Setiap item adalah sebuah Section (Kategori)
            val items = response?.data?.items ?: emptyList()
            
            if (items.isNotEmpty()) {
                homeLayoutCache = items
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        // 1. Ambil Layout Asli dari /home
        val allSections = fetchHomeLayout()

        // 2. Cari Section yang judulnya MENGANDUNG kata kunci request kita
        // Logika: Apakah "Trending" (API) mengandung "Trending" (Request)? Ya.
        val selectedSection = allSections.find { section ->
            val sectionTitle = section.title?.lowercase() ?: ""
            val requestKey = request.data.lowercase()
            
            // Pencocokan dua arah agar aman
            sectionTitle.contains(requestKey) || requestKey.contains(sectionTitle)
        }

        // 3. Ambil items (film) dari section tersebut
        // Jika tidak ketemu, return list kosong (JANGAN search manual agar tidak muncul film aneh)
        val movies = selectedSection?.items?.map { it.toSearchResponse(this) } ?: emptyList()

        return newHomePageResponse(request.name, movies)
    }

    // --- Search Tetap Pakai API LAMA (Stabil) ---
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.post(
                "$apiUrl/wefeed-h5-bff/web/subject/search",
                requestBody = mapOf(
                    "keyword" to query,
                    "page" to "1",
                    "perPage" to "20",
                    "subjectType" to "0",
                ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
            ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Detail & Load Tetap Pakai API LAMA ---
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")

        val document = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
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
            app.get("$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
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
        // Referer ke API Lama
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$apiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams?.first()?.id
        val format = streams?.first()?.format

        app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
            referer = referer
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

// --- Data Classes ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
)

// Struktur Data Home (API Baru)
data class HomeMedia(
    @JsonProperty("data") val data: Data? = null
) {
    data class Data(
        // Di endpoint /home, array utamanya bernama "items"
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf()
    )
}

// Struktur Data Search/Detail (API Lama)
data class Media(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
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

// Items Universal (Digunakan oleh API Baru & Lama)
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
    
    // Field nesting untuk API /home (Setiap section punya list items sendiri)
    @JsonProperty("items") val items: ArrayList<Items>? = null
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
