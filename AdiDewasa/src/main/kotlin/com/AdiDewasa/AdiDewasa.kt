package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
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
        get() {
            return listOf(
                MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
                MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
                MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
                MainPageData("Adult Movies - Sesuai Abjad (A-Z)", "2:3:adult"),
                MainPageData("Adult Movies - Klasik & Retro (Oldest)", "2:2:adult"),
                MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
                MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
                MainPageData("All Collections - Rekomendasi Terbaik", "-1:6:adult")
            )
        }

    private fun fixImgUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        if (url.startsWith("http")) return url
        val cleanUrl = if (url.startsWith("/")) url else "/$url"
        return "$mainUrl$cleanUrl"
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
            
            if (homeResponse?.success == false) {
                return newHomePageResponse(emptyList(), hasNext = false)
            }

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = searchResults,
                    isHorizontalImages = false
                ),
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
            val itemImage = this.image ?: this.poster
            val href = "$mainUrl/film/$itemSlug"
            return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
                this.posterUrl = fixImgUrl(itemImage)
            }
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            val url = "$mainUrl/api/live-search/$query"
            val response = app.get(url)
            val searchResponse = response.parsedSafe<ApiSearchResponse>()
            return searchResponse?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        try {
            val response = app.get(url)
            val doc = response.document
            
            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() 
                ?: doc.selectFirst("meta[property='og:title']")?.attr("content") 
                ?: "Unknown Title"

            val poster = fixImgUrl(
                doc.selectFirst("meta[property='og:image']")?.attr("content") 
                ?: doc.selectFirst("div.poster img")?.attr("src")
            )

            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() 
                ?: doc.selectFirst("meta[property='og:description']")?.attr("content") ?: ""

            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list, .episode-item").isNotEmpty()
            
            // TRICK: Kita simpan JUDUL di dalam data URL dipisahkan dengan tanda "|"
            // Supaya nanti di loadLinks kita bisa ambil judulnya untuk cari subtitle
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url
            val dataPayload = "$videoHref|$title" 

            val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
                val rTitle = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
                val rHref = it.selectFirst("a")?.attr("href") ?: ""
                if (rTitle.isNotEmpty() && rHref.isNotEmpty()) {
                    newMovieSearchResponse(rTitle, rHref, TvType.Movie) {
                        this.posterUrl = fixImgUrl(it.selectFirst("img")?.attr("data-src"))
                    }
                } else null
            }

            if (hasEpisodes) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val episodeText = it.text().trim()
                    val episodeHref = it.attr("href")
                    val episodeNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""^(\d+)$""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()

                    if (episodeHref.isNotEmpty()) {
                        // Untuk episode, kita juga pass judulnya
                        newEpisode("$episodeHref|$title") {
                            this.name = if(episodeNum != null) "Episode $episodeNum" else episodeText
                            this.episode = episodeNum
                        }
                    } else null
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.year = year; this.tags = genre; this.posterUrl = poster; this.plot = description; this.recommendations = recs
                }
            } else {
                return newMovieLoadResponse(title, url, TvType.Movie, dataPayload) {
                    this.year = year; this.tags = genre; this.posterUrl = poster; this.plot = description; this.recommendations = recs
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // --- INTEGRASI SUBSOURCE (LANGSUNG DI SINI) ---
    private suspend fun fetchSubSource(title: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            val cleanTitle = title.replace(Regex("""\(\d{4}\)"""), "").trim().replace(" ", "+")
            val searchUrl = "https://subsource.net/search/$cleanTitle"
            val searchDoc = app.get(searchUrl).document
            
            // Ambil hasil pencarian pertama
            val firstResult = searchDoc.selectFirst("div.movie-list div.movie-entry a") ?: return
            var detailHref = firstResult.attr("href")
            if (!detailHref.startsWith("http")) detailHref = "https://subsource.net$detailHref"

            val detailDoc = app.get(detailHref).document
            // Cari elemen subtitle bahasa Indonesia
            val indoItems = detailDoc.select("div.language-container:contains(Indonesian) div.subtitle-item, tr:contains(Indonesian)")

            indoItems.forEach { item ->
                val linkEl = item.selectFirst("a.download-button, a")
                val dwnUrl = linkEl?.attr("href")
                if (!dwnUrl.isNullOrEmpty()) {
                    val fullDwnUrl = if (dwnUrl.startsWith("http")) dwnUrl else "https://subsource.net$dwnUrl"
                    val releaseName = item.select("span.release-name, td:nth-child(1)").text().trim()
                    val subName = if(releaseName.isNotEmpty()) "Indo: $releaseName" else "Indonesian Sub"
                    
                    subtitleCallback(newSubtitleFile("Indonesian", fullDwnUrl))
                }
            }
        } catch (e: Exception) {
            // Ignore error search sub
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // PISAHKAN DATA URL DAN JUDUL
        val dataParts = data.split("|")
        val linkUrl = dataParts.getOrNull(0) ?: data
        val titleForSub = dataParts.getOrNull(1) ?: ""

        // 1. CARI SUBTITLE INDONESIA (Background process)
        if (titleForSub.isNotEmpty()) {
            fetchSubSource(titleForSub, subtitleCallback)
        }

        // 2. PROSES VIDEO UTAMA
        try {
            val doc = app.get(linkUrl).document
            val script = doc.select("script").find { it.html().contains("signedUrl") }?.html() ?: return false
            val signedUrlMatch = Regex("""signedUrl\s*=\s*['"]([^'"]+)['"]""").find(script)
            val signedUrl = signedUrlMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
            
            val res = app.get(signedUrl).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            val qualities = videoSource.keys().asSequence().toList().sortedByDescending { it.toIntOrNull() ?: 0 }
            var foundLink = false

            for (key in qualities) {
                val url = videoSource.optString(key)
                if (url.isNotEmpty()) {
                    callback(newExtractorLink(name, "$name ${key}p", url))
                    foundLink = true
                    
                    // Ambil Subtitle Bawaan (jika ada)
                    val subJson = resJson.optJSONObject("sub")
                    subJson?.optJSONArray(key)?.let { array ->
                        for (i in 0 until array.length()) {
                            val subUrl = array.getString(i)
                            if (subUrl.isNotEmpty()) {
                                val cleanSub = fixImgUrl(subUrl)
                                val lang = if(cleanSub.contains("indo") || cleanSub.contains("_id")) "Indonesian (Ori)" else "English"
                                subtitleCallback(newSubtitleFile(lang, cleanSub))
                            }
                        }
                    }
                    break 
                }
            }
            return foundLink
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
