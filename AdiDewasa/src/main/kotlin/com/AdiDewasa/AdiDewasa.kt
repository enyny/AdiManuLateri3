package com.AdiDewasa

import com.AdiDewasa.AdiDewasaExtractor.invokeAdiDewasaDirect
import com.AdiDewasa.AdiDewasaExtractor.invokeIdlix
import com.AdiDewasa.AdiDewasaExtractor.invokeMapple
import com.AdiDewasa.AdiDewasaExtractor.invokeSuperembed
import com.AdiDewasa.AdiDewasaExtractor.invokeVidfast
import com.AdiDewasa.AdiDewasaExtractor.invokeVidlink
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrccc
import com.AdiDewasa.AdiDewasaExtractor.invokeVixsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeWyzie
import com.AdiDewasa.AdiDewasaExtractor.invokeXprime
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val cfInterceptor by lazy { CloudflareKiller() }
    
    private val webHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Referer" to "$mainUrl/"
    )

    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
            MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
            MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
            MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
            MainPageData("All Collections - Rekomendasi", "-1:6:adult")
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val dataParts = request.data.split(":")
            val type = dataParts.getOrNull(0) ?: "-1"
            val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
            val adultFlag = dataParts.getOrNull(2) ?: "normal"
            val isAdultSection = adultFlag == "adult"

            val jsonPayload = """{
                "page": $page,
                "type": "$type",
                "country": -1,
                "sort": $sort,
                "adult": true,
                "adultOnly": $isAdultSection,
                "ignoreWatched": false,
                "genres": [],
                "keyword": ""
            }""".trimIndent()

            val payload = jsonPayload.toRequestBody("application/json".toMediaType())

            val response = app.post(
                "$mainUrl/api/filter", 
                requestBody = payload, 
                headers = webHeaders, 
                interceptor = cfInterceptor
            )
            
            val homeResponse = response.parsedSafe<HomeResponse>()
            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(request.name, searchResults, false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return newHomePageResponse(emptyList(), false)
        }
    }

    private fun MediaItem.toSearchResult(): SearchResponse? {
        val itemTitle = this.title ?: this.name ?: return null
        val itemSlug = this.slug ?: return null
        val itemImage = this.image?.takeIf { it.isNotEmpty() } ?: this.poster

        val href = "$mainUrl/film/$itemSlug"
        val posterUrl = if (!itemImage.isNullOrEmpty()) {
            if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
        } else null

        return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/api/live-search/$query"
        return try {
            app.get(url, headers = webHeaders, interceptor = cfInterceptor)
                .parsedSafe<ApiSearchResponse>()?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url, headers = webHeaders, interceptor = cfInterceptor)
        val document = response.document
        
        // --- CLEANER LOGIC START ---
        val ogTitle = document.select("meta[property=og:title]").attr("content")
        val fallbackTitle = document.selectFirst("h1")?.text() ?: "Unknown Title"
        val rawTitle = if (ogTitle.isNotEmpty()) ogTitle else fallbackTitle

        // Membersihkan judul dari sampah SEO seperti "Watch Eva - Movie subbed..."
        val title = rawTitle
            .replace(Regex("(?i)^Watch\\s+"), "") // Hapus "Watch " di depan
            .substringBefore(" - Movie")          // Hapus suffix " - Movie..."
            .substringBefore(" subbed")           // Hapus suffix " subbed..."
            .substringBefore(" online")           // Hapus suffix " online..."
            .substringBefore(",")                 // Hapus koma dan setelahnya (opsional)
            .trim()

        val poster = document.select("meta[property=og:image]").attr("content")
        val desc = document.select("meta[property=og:description]").attr("content")
        
        // Logika Tahun
        var year = Regex("\\d{4}").find(title)?.value?.toIntOrNull()
        if (year == null) {
             year = Regex("\\d{4}").find(document.text())?.value?.toIntOrNull()
        }
        
        // Logika Episode
        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val text = it.text()
            val href = it.attr("href")
            val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() 
                ?: Regex("""Episode\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            
            if (epNum == null) return@mapNotNull null
            
            newEpisode(
                LinkData(
                    url = fixUrl(href),
                    title = title, // Judul sudah bersih di sini
                    year = year,
                    season = 1,
                    episode = epNum
                ).toJson()
            ) {
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, tvType, LinkData(url, title, year).toJson()) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        val res = parseJson<LinkData>(data)
        
        // Pastikan judul bersih dikirim ke extractor
        val cleanTitle = res.title.trim()

        runAllAsync(
            { invokeAdiDewasaDirect(res.url, callback, subtitleCallback) },
            // Gunakan cleanTitle untuk extractor eksternal agar pencarian akurat
            { invokeIdlix(cleanTitle, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidsrc(null, res.season, res.episode, subtitleCallback, callback) },
            { invokeXprime(null, cleanTitle, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidfast(null, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidlink(null, res.season, res.episode, callback) },
            { invokeMapple(null, res.season, res.episode, subtitleCallback, callback) },
            { invokeVixsrc(null, res.season, res.episode, callback) }
        )

        return true
    }
}
