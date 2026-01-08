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
    
    // URL API Baru (Sesuai dengan sumber data aplikasi asli di screenshot)
    private val homeApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=moviebox.ph"

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

    // Token & Header
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

    // Kita hanya butuh satu menu "Home" karena API akan memuat semua kategori
    override val mainPage = mainPageOf(
        "home" to "Home"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse? {
        if (page > 1) return newHomePageResponse(emptyList(), hasNext = false)

        val response = app.get(homeApiUrl, headers = headers).parsedSafe<HomeResponse>()
        val homePageList = ArrayList<HomePageList>()

        response?.data?.operatingList?.forEach { section ->
            if (section.type == "FILTER" || section.type == "SPORT_LIVE" || section.type == "CUSTOM_VIP") return@forEach

            val items = ArrayList<SearchResponse>()
            
            val sourceList = when (section.type) {
                "BANNER" -> section.banner?.items
                "CUSTOM" -> section.customData?.items
                else -> section.subjects
            }

            sourceList?.forEach { item ->
                val title = item.title ?: item.subject?.title
                val id = item.subjectId ?: item.id
                val coverUrl = item.image?.url ?: item.cover?.url ?: item.subject?.cover?.url
                
                if (!title.isNullOrEmpty() && !id.isNullOrEmpty() && id != "0") {
                    items.add(
                        newMovieSearchResponse(title, "$mainUrl/detail/$id") {
                            this.posterUrl = coverUrl
                            this.quality = SearchQuality.HD
                        }
                    )
                }
            }

            if (items.isNotEmpty()) {
                val sectionTitle = section.title ?: "Featured"
                val isHorizontal = section.type == "BANNER" 
                
                homePageList.add(
                    HomePageList(
                        sectionTitle,
                        items,
                        isHorizontalImages = isHorizontal
                    )
                )
            }
        }

        return newHomePageResponse(homePageList, hasNext = false)
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
            val seasonList = document?.resource?.seasons
            
            if (!seasonList.isNullOrEmpty()) {
                seasonList.forEach { seasonData ->
                    val epList = if (seasonData.allEp.isNullOrEmpty()) {
                        (1..(seasonData.maxEp ?: 1)).toList()
                    } else {
                        seasonData.allEp.split(",").mapNotNull { it.toIntOrNull() }
                    }
                    
                    epList.forEach { epNum ->
                        val epData = LoadData(id, seasonData.se, epNum, subject?.detailPath).toJson()
                        episodes.add(
                            newEpisode(epData) {
                                this.season = seasonData.se
                                this.episode = epNum
                                this.name = "Episode $epNum"
                            }
                        )
                    }
                }
            } else {
                val epData = LoadData(id, 1, 1, subject?.detailPath).toJson()
                episodes.add(newEpisode(epData) { this.name = "Episode 1" })
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score // PERBAIKAN: Menggunakan this.score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            val movieData = LoadData(id, detailPath = subject?.detailPath).toJson()
            return newMovieLoadResponse(title, url, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score // PERBAIKAN: Menggunakan this.score
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

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        if (id != null && format != null) {
            app.get(
                "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
                referer = referer
            ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        subtitle.lanName ?: "Unknown",
                        subtitle.url ?: return@map
                    )
                )
            }
        }
        return true
    }

    // ================= DATA CLASSES =================

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
    
    data class HomeItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("image") val image: ImageObj? = null,
        @JsonProperty("cover") val cover: ImageObj? = null,
        @JsonProperty("subject") val subject: Items? = null 
    )

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

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("id") val id: String? = null,
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
