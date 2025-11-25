package com.AdiDewasa

// Import Extractor Pihak Ketiga (Pastikan file AdiDewasaExtractor.kt ada)
import com.AdiDewasa.AdiDewasaExtractor.invokeIdlix
import com.AdiDewasa.AdiDewasaExtractor.invokeMapple
import com.AdiDewasa.AdiDewasaExtractor.invokeVidfast
import com.AdiDewasa.AdiDewasaExtractor.invokeVidlink
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeXprime
import com.AdiDewasa.AdiDewasaExtractor.invokeVixsrc

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

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
            val response = app.post("$mainUrl/api/filter", requestBody = payload)
            
            val homeResponse = response.parsedSafe<HomeResponse>()
            
            if (homeResponse?.success == false) {
                return newHomePageResponse(emptyList(), hasNext = false)
            }

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(request.name, searchResults, false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return newHomePageResponse(emptyList(), hasNext = false)
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
            app.get(url).parsedSafe<ApiSearchResponse>()?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        // 1. PEMBERSIH JUDUL (Agar Idlix/Vidsrc bisa membaca)
        val ogTitle = document.select("meta[property=og:title]").attr("content")
        val fallbackTitle = document.selectFirst("h1")?.text() ?: "Unknown Title"
        val rawTitle = if (ogTitle.isNotEmpty()) ogTitle else fallbackTitle

        val title = rawTitle
            .replace(Regex("(?i)^Watch\\s+"), "") // Hapus "Watch "
            .substringBefore(" - Movie")
            .substringBefore(" subbed")
            .substringBefore(" online")
            .trim()

        val poster = document.select("meta[property=og:image]").attr("content")
        val desc = document.select("meta[property=og:description]").attr("content")
        
        var year = Regex("\\d{4}").find(title)?.value?.toIntOrNull()
        if (year == null) year = Regex("\\d{4}").find(document.text())?.value?.toIntOrNull()
        
        val tags = document.select("div.genre-list a, .genres a").map { it.text() }

        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val text = it.text()
            val href = it.attr("href")
            val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() 
                ?: Regex("""Episode\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            
            if (epNum == null) return@mapNotNull null
            
            // Kita bungkus URL + Title di dalam JSON LinkData
            val data = LinkData(
                url = fixUrl(href),
                title = title,
                year = year,
                season = 1,
                episode = epNum
            )
            
            newEpisode(data.toJson()) { // Kirim JSON, bukan URL mentah
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        val recommendations = document.select("div.film_list-wrap div.flw-item").mapNotNull {
            val recTitle = it.select("h3.film-name a").text()
            val recHref = it.select("h3.film-name a").attr("href")
            val recPoster = it.select("img.film-poster-img").attr("data-src")
            if (recTitle.isEmpty()) return@mapNotNull null
            newMovieSearchResponse(recTitle, "$mainUrl$recHref", TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie
        
        // Bungkus data Movie juga ke dalam JSON LinkData
        val movieData = LinkData(url, title, year).toJson()

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, tvType, movieData) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false

        // 1. Parse JSON LinkData (PENTING!)
        // File lama gagal karena tidak melakukan parsing ini
        val linkData = try {
            parseJson<LinkData>(data)
        } catch (e: Exception) {
            // Fallback jika data ternyata URL biasa (untuk kompatibilitas)
            LinkData(url = data, title = "")
        }

        val url = linkData.url
        val cleanTitle = if (linkData.title.isNotEmpty()) linkData.title else null

        // 2. Jalankan Internal Extractor (AdiDewasa)
        // Kita gunakan logika dari file "Working" tapi dibungkus agar aman
        try {
            val doc = app.get(url).document // Gunakan 'url' yang sudah di-parse, bukan 'data' mentah
            val allScripts = doc.select("script").joinToString(" ") { it.data() }
            
            // Regex dari file Working yang terbukti ampuh
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(allScripts)
                ?.groupValues?.get(1)?.replace("\\/", "/")
            
            if (signedUrl != null) {
                val jsonText = app.get(
                    signedUrl, 
                    referer = url,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text
                
                val json = JSONObject(jsonText)
                val videoSource = json.optJSONObject("video_source")
                
                if (videoSource != null) {
                    val qualities = videoSource.keys().asSequence().toList()
                    qualities.forEach { qualityStr ->
                        val link = videoSource.optString(qualityStr)
                        if (link.isNotEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    this.name,
                                    "AdiDewasa $qualityStr",
                                    link,
                                    ExtractorLinkType.M3U8
                                ) {
                                    this.referer = url
                                }
                            )
                        }
                    }
                    
                    // Subtitles
                    val subJson = json.optJSONObject("sub")
                    val bestQuality = qualities.maxByOrNull { it.toIntOrNull() ?: 0 }
                    if (bestQuality != null) {
                        val subs = subJson?.optJSONArray(bestQuality)
                        if (subs != null) {
                            for (i in 0 until subs.length()) {
                                val subPath = subs.getString(i)
                                val subUrl = if (subPath.startsWith("http")) subPath else "$mainUrl$subPath"
                                subtitleCallback.invoke(newSubtitleFile("English", subUrl))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Jalankan Extractor Luar (Idlix, Vidsrc, dll)
        // Ini akan berjalan paralel dengan internal, memberikan lebih banyak opsi
        if (cleanTitle != null) {
            runAllAsync(
                { invokeIdlix(cleanTitle, linkData.year, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeVidsrc(null, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeXprime(null, cleanTitle, linkData.year, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeVidfast(null, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeVidlink(null, linkData.season, linkData.episode, callback) },
                { invokeMapple(null, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeVixsrc(null, linkData.season, linkData.episode, callback) }
            )
        }

        return true
    }
}
