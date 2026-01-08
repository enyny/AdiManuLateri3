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
    private val apiUrl = "https://fmoviesunblocked.net"
    
    // URL API Baru
    private val homeApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=moviebox.ph"
    private val trendingApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/trending"

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

    // Header lengkap dengan Authorization Token
    private val headers = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjU0NzY2NTA2MTAxMzU1NTEzNDQsImF0cCI6MywiZXh0IjoiMTc2Nzg5MTEyMyIsImV4cCI6MTc3NTY2NzEyMywiaWF0IjoxNzY3ODkwODIzfQ.EFoAU--LYZMmyq83WvAwvVnGQiTJ27daeP3HWuA98VA",
        "content-type" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        "sec-ch-ua" to "\"Chromium\";v=\"107\", \"Not=A?Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "user-agent" to "Mozilla/5.0 (Linux; Android 13; CPH2235) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36",
        "x-request-lang" to "en"
    )

    // Menu Utama Sederhana: Home (Dinamis) & Trending (API Khusus)
    override val mainPage = mainPageOf(
        "home" to "Home (Sesuai Aplikasi)",
        "trending" to "🔥 Semua Trending"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse? {
        val items = ArrayList<HomePageList>()

        // === 1. LOGIKA HALAMAN TRENDING (OPSIONAL) ===
        if (request.data == "trending") {
            val pageNum = if (page <= 1) 0 else page - 1
            val url = "$trendingApiUrl?page=$pageNum&perPage=18"
            
            val response = app.get(url, headers = headers).parsedSafe<TrendingResponse>()
            val trendingItems = ArrayList<SearchResponse>()

            response?.data?.subjectList?.forEach { item ->
                val title = item.title ?: ""
                val id = item.subjectId ?: ""
                val coverUrl = item.cover?.url ?: ""
                val type = if (item.subjectType == 2) TvType.TvSeries else TvType.Movie
                
                if (title.isNotEmpty() && id.isNotEmpty()) {
                    val searchResponse = if (type == TvType.TvSeries) {
                        newTvSeriesSearchResponse(title, "$mainUrl/detail/$id", TvType.TvSeries) {
                            this.posterUrl = coverUrl
                            this.quality = SearchQuality.HD
                        }
                    } else {
                        newMovieSearchResponse(title, "$mainUrl/detail/$id", TvType.Movie) {
                            this.posterUrl = coverUrl
                            this.quality = SearchQuality.HD
                        }
                    }
                    trendingItems.add(searchResponse)
                }
            }

            if (trendingItems.isNotEmpty()) {
                items.add(HomePageList("Trending Now", trendingItems, isHorizontalImages = false))
            }
            
            return newHomePageResponse(items, hasNext = true)
        }

        // === 2. LOGIKA HALAMAN HOME (SESUAI SCREENSHOT) ===
        if (page > 1) return newHomePageResponse(emptyList(), hasNext = false)

        // Mengambil data dari API Home yang berisi semua kategori (Bromance, Killers, dll)
        val response = app.get(homeApiUrl, headers = headers).parsedSafe<HomeResponse>()
        
        response?.data?.operatingList?.forEach { section ->
            // Filter tipe yang tidak ingin ditampilkan (misal: tombol filter atau iklan VIP)
            if (section.type == "FILTER" || section.type == "SPORT_LIVE" || section.type == "CUSTOM_VIP") return@forEach

            val sectionItems = ArrayList<SearchResponse>()
            
            // Ambil daftar film dari berbagai struktur JSON yang mungkin ada
            val sourceList = when (section.type) {
                "BANNER" -> section.banner?.items
                "CUSTOM" -> section.customData?.items
                else -> section.subjects
            }

            sourceList?.forEach { item ->
                // Fallback untuk judul dan gambar karena struktur JSON bisa berbeda-beda
                val title = item.title ?: item.subject?.title
                val id = item.subjectId ?: item.id
                val coverUrl = item.image?.url ?: item.cover?.url ?: item.subject?.cover?.url
                
                // Pastikan item valid sebelum ditambahkan
                if (!title.isNullOrEmpty() && !id.isNullOrEmpty() && id != "0") {
                    sectionItems.add(
                        newMovieSearchResponse(title, "$mainUrl/detail/$id") {
                            this.posterUrl = coverUrl
                            this.quality = SearchQuality.HD
                        }
                    )
                }
            }

            // Jika ada item, tambahkan sebagai kategori baru (Judul Kategori dari API)
            if (sectionItems.isNotEmpty()) {
                val categoryName = section.title ?: "Featured"
                // Banner biasanya gambar lebar (horizontal)
                val isHorizontal = section.type == "BANNER" 
                
                items.add(
                    HomePageList(
                        categoryName,
                        sectionItems,
                        isHorizontalImages = isHorizontal
                    )
                )
            }
        }

        return newHomePageResponse(items, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val bodyMap = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "0",
            "subjectType" to "0",
        )
        val body = bodyMap.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        return app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search",
            requestBody = body,
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<SearchData>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Search failed or returned no results.")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        // Menggunakan endpoint detail lama (atau sesuaikan jika ada yang baru)
        val detailUrl = "$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id"
        val document = app.get(detailUrl).parsedSafe<MediaDetail>()?.data
        val subject = document?.subject
        
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString())
        
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }

        val isTv = subject?.subjectType == 2
        
        if (isTv) {
            val episodes = ArrayList<Episode>()
            // Coba parsing episode dari struktur resource.seasons (logika lama)
            // Jika kosong, buat dummy range
            val seasonList = document?.resource?.seasons
            if (!seasonList.isNullOrEmpty()) {
                seasonList.forEach { seasonData ->
                    val epList = if (seasonData.allEp.isNullOrEmpty()) {
                        (1..(seasonData.maxEp ?: 1)).toList()
                    } else {
                        seasonData.allEp.split(",").mapNotNull { it.toIntOrNull() }
                    }
                    
                    epList.forEach { epNum ->
                        episodes.add(
                            newEpisode(LoadData(id, seasonData.se, epNum, subject?.detailPath).toJson()) {
                                this.season = seasonData.se
                                this.episode = epNum
                                this.name = "Episode $epNum"
                            }
                        )
                    }
                }
            } else {
                // Fallback jika tidak ada info season detail
                episodes.add(newEpisode(LoadData(id, 1, 1, subject?.detailPath).toJson()) {
                    this.name = "Episode 1"
                })
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(
                title, url, TvType.Movie, 
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = score
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
        val media = parseJson<LoadData>(data)
        // Header referer penting agar tidak 403 Forbidden
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        // Menggunakan endpoint play yang lama (apiUrl = fmoviesunblocked)
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

        // Load Subtitles
        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        if (id != null && format != null) {
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
        }
        return true
    }

    // ================= DATA CLASSES =================

    // --- Data Class untuk Home API Baru ---
    data class HomeResponse(@JsonProperty("data") val data: HomeData? = null)
    data class HomeData(@JsonProperty("operatingList") val operatingList: List<OperatingList>? = null)
    
    data class OperatingList(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("banner") val banner: BannerSection? = null,
        @JsonProperty("subjects") val subjects: List<HomeItem>? = null,
        @JsonProperty("customData") val customData: CustomDataSection? = null
    )
    data class BannerSection(@JsonProperty("items") val items: List<HomeItem>? = null)
    data class CustomDataSection(@JsonProperty("items") val items: List<HomeItem>? = null)
    
    // Struktur item di Home bisa berbeda-beda
    data class HomeItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("image") val image: ImageObj? = null,
        @JsonProperty("cover") val cover: ImageObj? = null,
        @JsonProperty("subject") val subject: Items? = null // Reuse 'Items' class
    )
    
    // --- Data Class untuk Trending API ---
    data class TrendingResponse(@JsonProperty("data") val data: TrendingData? = null)
    data class TrendingData(@JsonProperty("subjectList") val subjectList: List<Items>? = null)

    // --- Data Class Umum (Search & Detail) ---
    data class SearchData(@JsonProperty("data") val data: SearchInnerData? = null)
    data class SearchInnerData(@JsonProperty("items") val items: List<Items>? = null)

    data class Media(@JsonProperty("data") val data: MediaData? = null)
    data class MediaData(
        @JsonProperty("items") val items: List<Items>? = null,
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf()
    ) {
        data class Streams(
            @JsonProperty("id") val id: String? = null,
            @JsonProperty("format") val format: String? = null,
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolutions") val resolutions: String? = null,
        )
        data class Captions(
            @JsonProperty("lanName") val lanName: String? = null,
            @JsonProperty("url") val url: String? = null,
        )
    }

    data class MediaDetail(@JsonProperty("data") val data: MediaDetailData? = null)
    data class MediaDetailData(
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

    // Class utama untuk menampung properti film/serial
    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("id") val id: String? = null, // Kadang id ada di sini
        @JsonProperty("subjectType") val subjectType: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("cover") val cover: ImageObj? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("trailer") val trailer: Trailer? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
    ) {
        fun toSearchResponse(provider: Adimoviebox): SearchResponse {
            val finalId = subjectId ?: id ?: ""
            val url = "${provider.mainUrl}/detail/$finalId"
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
    }

    data class ImageObj(@JsonProperty("url") val url: String? = null)
    
    data class Trailer(
        @JsonProperty("videoAddress") val videoAddress: ImageObj? = null,
    )

    data class LoadData(
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
    )
}
