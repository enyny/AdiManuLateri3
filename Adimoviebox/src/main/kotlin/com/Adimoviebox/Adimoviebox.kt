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

// ✅ Import Wajib
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

    // =========================================================================
    // HEADER DARI LOG HAR (JANGAN DIUBAH KECUALI PERLU)
    // =========================================================================
    private val commonHeaders = mapOf(
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        // Token ini valid sampai April 2026 (Sesuai Log)
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjgwOTI1MjM4NzUxMDUzOTI2NTYsImF0cCI6MywiZXh0IjoiMTc2NzYxNTY5MCIsImV4cCI6MTc3NTM5MTY5MCwiaWF0IjoxNzY3NjE1MzkwfQ.p_U5qrxe_tQyI5RZJxZYcQD3SLqY-mUHVJd00M3vWU0",
        "content-type" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        // Meniru Browser Linux Chrome (Sesuai Log) agar tidak diblokir
        "sec-ch-ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Linux\"",
        "sec-fetch-dest" to "empty",
        "sec-fetch-mode" to "cors",
        "sec-fetch-site" to "cross-site",
        "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
        // Timezone disesuaikan dengan Log HAR (Jayapura)
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
        "x-request-lang" to "en"
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
        } ?: throw ErrorLoadingException("Gagal memuat kategori '${request.name}'.")

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

    override suspend fun load(url: String): LoadResponse {
        // Membersihkan URL dari slash di akhir agar ID bersih
        val id = url.trimEnd('/').substringAfterLast("/")
        
        val response = app.get(
            "$apiUrl/wefeed-h5api-bff/web/subject/detail?subjectId=$id",
            headers = commonHeaders
        ).parsedSafe<MediaDetail>()

        // Validasi Ekstra untuk data null
        val document = response?.data ?: throw ErrorLoadingException("Data detail tidak ditemukan (Null Response).")
        val subject = document.subject ?: throw ErrorLoadingException("Info film kosong (Subject Null).")
        
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
                this.posterUrl
