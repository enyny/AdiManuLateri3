package com.AdiDewas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.lagradost.cloudstream3.runAllAsync // Penting untuk jalan paralel

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- Main Page Configuration ---
    override val mainPage: List<MainPageData>
        get() {
            return listOf(
                MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
                MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
                MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
                MainPageData("Adult Movies - Sesuai Abjad (A-Z)", "2:3:adult"),
                MainPageData("Adult Movies - Klasik (Oldest)", "2:2:adult"),
                MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
                MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
                MainPageData("Adult TV Shows - Rating Tertinggi", "1:6:adult"),
                MainPageData("All Collections - Baru Ditambahkan", "-1:1:adult"),
                MainPageData("All Collections - Paling Sering Ditonton", "-1:5:adult")
            )
        }

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
            
            if (homeResponse?.success == false) return newHomePageResponse(emptyList(), hasNext = false)

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(request.name, searchResults, isHorizontalImages = false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    private fun MediaItem.toSearchResult(): SearchResponse? {
        try {
            val itemTitle = this.title ?: this.name ?: "Unknown Title"
            val itemSlug = this.slug ?: return null
            val itemImage = this.image ?: this.poster ?: ""
            val href = "$mainUrl/film/$itemSlug"
            val posterUrl = if (itemImage.isNotEmpty()) {
                if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
            } else ""

            return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) { return null }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            val url = "$mainUrl/api/live-search/$query"
            val response = app.get(url)
            val searchResponse = response.parsedSafe<ApiSearchResponse>()
            return searchResponse?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) { return null }
    }

    override suspend fun load(url: String): LoadResponse {
        try {
            val doc = app.get(url).document
            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
            
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            
            // Bersihkan judul dari tahun untuk pencarian subtitle nanti
            val cleanTitle = title.replace(Regex("""\(\d{4}\)"""), "").trim()

            val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
                val t = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
                val i = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src") ?: ""
                val h = it.selectFirst("a")?.attr("href") ?: ""
                if (t.isNotEmpty() && h.isNotEmpty()) {
                    newMovieSearchResponse(t, h, TvType.Movie) {
                        this.posterUrl = if (i.startsWith("http")) i else mainUrl + i
                    }
                } else null
            }

            if (hasEpisodes) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val epText = it.text().trim()
                    val epHref = it.attr("href")
                    val epNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()

                    if (epHref.isNotEmpty()) {
                        newEpisode(epHref) {
                            this.name = "Episode ${epNum ?: epText}"
                            this.episode = epNum
                            this.season = 1 // Default season 1 for drama
                        }
                    } else null
                }
                return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
                    this.year = year
                    this.tags = genre
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recs
                }
            } else {
                return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
                    this.year = year
                    this.tags = genre
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recs
                }
            }
        } catch (e: Exception) { throw e }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val doc = app.get(data).document
            
            // 1. METADATA EXTRACTION FOR SUBTITLES
            val pageTitle = doc.selectFirst("h1.title")?.text() ?: ""
            val year = Regex("""\((\d{4})\)""").find(pageTitle)?.groupValues?.get(1)?.toIntOrNull()
            val cleanTitle = pageTitle.replace(Regex("""\(\d{4}\)"""), "").replace(Regex("Episode.*"), "").trim()
            
            // Cek episode
            val episodeNum = Regex("""Episode\s*(\d+)""").find(pageTitle)?.groupValues?.get(1)?.toIntOrNull()
            val isSeries = episodeNum != null
            val seasonNum = if (isSeries) 1 else null
            val type = if (isSeries) TvType.TvSeries else TvType.Movie

            // 2. VIDEO EXTRACTION (INTERNAL)
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return false
            
            val res = app.get(signedUrl).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            val qualities = videoSource.keys().asSequence().toList().sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull()
            
            // Kirim Link Video
            videoSource.keys().forEach { quality ->
                 val link = videoSource.getString(quality)
                 if (link.isNotEmpty()) {
                     callback(
                        newExtractorLink(
                            name,
                            "$name ($quality)",
                            link,
                            ExtractorLinkType.INFER // Biarkan otomatis deteksi
                        )
                     )
                 }
            }
            
            // 3. INTERNAL SUBTITLES (DARI SERVER ADIDEWASA)
            if (bestQualityKey != null) {
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        subtitleCallback(newSubtitleFile("English (Internal)", mainUrl + subUrl))
                    }
                }
            }

            // 4. EXTERNAL SUBTITLES (OPENSUBTITLES & WYZIE)
            // Dijalankan secara parallel agar tidak delay
            runAllAsync {
                val imdbId = getImdbIdFromTitle(cleanTitle, year, type)
                if (imdbId != null) {
                    // Panggil OpenSubtitles v3
                    invokeSubtitleAPI(imdbId, seasonNum, episodeNum, subtitleCallback)
                    // Panggil WyZIE
                    invokeWyZIESUBAPI(imdbId, seasonNum, episodeNum, subtitleCallback)
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
