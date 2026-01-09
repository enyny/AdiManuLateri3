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
    
    // API LAMA (Stabil untuk Search, Detail, & Play)
    private val apiUrl = "https://filmboom.top" 
    
    // API BARU (Untuk Home Page agar Akurat)
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

    // Headers wajib untuk API Baru
    private val homeHeaders = mapOf(
        "Authority" to "h5-api.aoneroom.com",
        "Accept" to "application/json",
        "Origin" to "https://moviebox.ph",
        "Referer" to "https://moviebox.ph/",
        "x-request-lang" to "en"
    )

    // DAFTAR KATEGORI
    // Kunci (Kiri) saya ubah menjadi TEKS UNIK yang pasti ada di judul asli.
    // Saya HAPUS emoji di bagian Kunci agar pencocokan lebih akurat (Server kadang beda emoji).
    override val mainPage: List<MainPageData> = mainPageOf(
        "Trending" to "Trending🔥",
        "Indonesian Movies" to "Trending Indonesian Movies",
        "Indonesian Drama" to "Trending Indonesian Drama💗",
        "Short TV" to "🔥Hot Short TV",
        "New Release" to "K-Drama: New Release", // Kunci: 'New Release' (lebih aman)
        "Animeverse" to "Into Animeverse🌟",
        "Bromance" to "👨‍❤️‍👨 Bromance",
        "Indonesian Killers" to "Indonesian Killers",
        "Upcoming" to "Upcoming Calendar",
        "Western TV" to "Western TV",
        "Keluargaku yang Lucu" to "Keluargaku yang Lucu 🏠",
        "Hollywood" to "Hollywood Movies",
        "Eaten by the Rich" to "We Won’t Be Eaten by the Rich!",
        "Animals" to "Cute World of Animals",
        "C-Drama" to "C-Drama",
        "Escape Death" to "Run!! 🩸Escape Death!",
        "No Regrets" to "No Regrets for Loving You",
        "Indo Dubbed" to "Must Watch Indo Dubbed",
        "Horror" to "Midnight Horror",
        "Defeat Me" to "HA！Nobody Can Defeat Me",
        "Cyberpunk" to "🎮 Cyberpunk World",
        "Animated" to "Animated Flim",
        "Monster" to "Awas! Monster & Titan",
        "Thai-Drama" to "Tredning Thai-Drama",
        "Fake Marriage" to "👰Fake Marriage"
    )

    // Cache agar tidak spam request ke server
    private var homePageCache: List<Items> = emptyList()

    // Fungsi mengambil Data Home dari API Baru
    private suspend fun fetchHomeCategories(): List<Items> {
        if (homePageCache.isNotEmpty()) return homePageCache

        val allCategories = mutableListOf<Items>()
        // UPDATE: Scan lebih dalam sampai Page 4 (Total 5 Halaman)
        // Ini memastikan kategori yang ada di bawah (seperti Thai-Drama) tetap ketemu.
        for (i in 0..4) {
            try {
                val response = app.get(
                    "$homeApiUrl/wefeed-h5api-bff/subject/trending?page=$i&perPage=20",
                    headers = homeHeaders
                ).parsedSafe<HomeMedia>()
                
                response?.data?.subjectList?.let { allCategories.addAll(it) }
            } catch (e: Exception) {
                // Ignore error per page
            }
        }
        
        // Simpan ke cache jika berhasil dapat data
        if (allCategories.isNotEmpty()) {
            homePageCache = allCategories
        }
        return allCategories
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        // 1. Ambil data asli dari server
        val allData = fetchHomeCategories()

        // 2. Cari kategori yang judulnya MENGANDUNG kata kunci kita
        // Contoh: Judul Server "🔥Hot Short TV" mengandung Kunci "Short TV" -> MATCH!
        val selectedCategory = allData.find { 
            val apiTitle = it.title?.lowercase() ?: ""
            val reqKey = request.data.lowercase()
            apiTitle.contains(reqKey)
        }

        // 3. AMBIL DATA FILM
        // PERBAIKAN: Saya HAPUS fallback search().
        // Jika kategori tidak ketemu, biarkan kosong (Empty List).
        // Ini mencegah munculnya film "SpongeBob" atau film acak lainnya.
        val movies = selectedCategory?.items?.map { it.toSearchResponse(this) } ?: emptyList()

        return newHomePageResponse(request.name, movies)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // Fungsi Search tetap pakai API LAMA (Stabil)
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

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")

        // Detail menggunakan API LAMA
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

        // Play menggunakan API LAMA
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

// Struktur Data untuk API Baru (Home)
data class HomeMedia(
    @JsonProperty("data") val data: Data? = null
) {
    data class Data(
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf()
    )
}

// Struktur Data untuk API Lama (Search/Detail)
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

// Items Universal (Bisa dipakai API Baru & Lama)
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
    
    // Field khusus API Baru (Nesting)
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
