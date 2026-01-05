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

// ✅ Import wajib
import com.lagradost.cloudstream3.utils.INFER_TYPE 

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://h5-api.aoneroom.com"
    private val playApiUrl = "https://filmboom.top"
    
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

    // ✅ HEADER DISEDERHANAKAN
    // Kita hapus header 'sec-' yang rumit karena sering bikin error di Cloudstream
    private val commonHeaders = mapOf(
        "accept" to "application/json",
        // Masukkan Token Baru Kamu Di Sini (Pastikan tidak ada spasi di awal/akhir)
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0",
        "content-type" to "application/json",
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "Trending🔥" to "Trending🔥",
        "Trending Indonesian Movies" to "Trending Indonesian Movies",
        "Trending Indonesian Drama💗" to "Trending Indonesian Drama💗",
        "🔥Hot Short TV" to "🔥Hot Short TV",
        "K-Drama: New Release" to "K-Drama: New Release",
        "Into Animeverse🌟" to "Into Animeverse🌟",
        "👨‍❤️‍👨 Bromance" to "👨‍❤️‍👨 Bromance",
        "Indonesian Killers" to "Indonesian Killers",
        "Upcoming Calendar" to "Upcoming Calendar",
        "Western TV" to "Western TV",
        "Keluargaku yang Lucu 🏠" to "Keluargaku yang Lucu 🏠",
        "Hollywood Movies" to "Hollywood Movies",
        "We Won’t Be Eaten by the Rich!" to "We Won’t Be Eaten by the Rich!",
        "Cute World of Animals" to "Cute World of Animals",
        "C-Drama" to "C-Drama",
        "Run!! 🩸Escape Death!" to "Run!! 🩸Escape Death!",
        "No Regrets for Loving You" to "No Regrets for Loving You",
        "Must Watch Indo Dubbed" to "Must Watch Indo Dubbed",
        "Midnight Horror" to "Midnight Horror",
        "HA！Nobody Can Defeat Me" to "HA！Nobody Can Defeat Me",
        "🎮 Cyberpunk World" to "🎮 Cyberpunk World",
        "Animated Flim" to "Animated Flim",
        "Awas! Monster & Titan" to "Awas! Monster & Titan",
        "Tredning Thai-Drama" to "Tredning Thai-Drama",
        "👰Fake Marriage" to "👰Fake Marriage"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val response = app.get(
            "$apiUrl/wefeed-h5api-bff/home?host=moviebox.ph", 
            headers = commonHeaders
        ).parsedSafe<HomeResponse>()

        val targetCategory = response?.data?.operatingList?.find { 
            it.title?.trim() == request.name.trim() 
        }

        val filmList = targetCategory?.subjects?.mapNotNull {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Home Gagal. Coba Refresh atau Update Token.")

        return newHomePageResponse(request.name, filmList)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$apiUrl/wefeed-h5api-bff/subject/search-suggest", 
            requestBody = mapOf(
                "keyword" to query,
                "perPage" to 20
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()),
            headers = commonHeaders
        ).parsedSafe<Media>()?.data?.items?.mapNotNull { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian gagal.")
    }

    // ✅ FUNGSI LOAD DENGAN DEBUGGER
    override suspend fun load(url: String): LoadResponse {
        val id = url.trimEnd('/').substringAfterLast("/")
        
        // 1. Ambil data mentah (Text) dulu, jangan langsung di-parse
        val rawResponse = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$id",
            headers = commonHeaders
        ).text

        // 2. Cek apakah Token expired atau Error server
        if (rawResponse.contains("Unauthorized") || rawResponse.contains("\"code\":401")) {
            throw ErrorLoadingException("Token EXPIRED. Ambil token baru di Kiwi Browser!")
        }

        // 3. Coba Parse Manual
        val mediaDetail = try {
            parseJson<MediaDetail>(rawResponse)
        } catch (e: Exception) {
            throw ErrorLoadingException("Format JSON Berubah: $rawResponse")
        }

        val document = mediaDetail.data
        val subject = document?.subject 
        
        // 4. Jika data kosong, tampilkan pesan error dari servernya
        if (subject == null) {
             throw ErrorLoadingException("Data Kosong! Server Merespon: $rawResponse")
        }
        
        val title = subject.title ?: "No Title"
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        val score = Score.from10(subject.imdbRatingValue) 
        
        val actors = document.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
            headers = commonHeaders
        ).parsedSafe<Media>()?.data?.items?.mapNotNull {
            it.toSearchResponse(this)
        }

        if (tvType == TvType.TvSeries) {
            val episodes = document.resource?.seasons?.flatMap { seasons ->
                val epList = if (seasons.allEp.isNullOrEmpty()) {
                    (1..(seasons.maxEp ?: 0)).toList()
                } else {
                    seasons.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                }

                epList.map { epNum ->
                    newEpisode(
                        LoadData(
                            id,
                            seasons.se,
                            epNum,
                            subject.detailPath
                        ).toJson()
                    ) {
                        this.season = seasons.se
                        this.episode = epNum
                    }
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
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
        
        if (media.detailPath.isNullOrEmpty()) return false

        val playReferer = "$playApiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&detailSe=&detailEp=&lang=en"

        val playHeaders = mapOf(
            "authority
