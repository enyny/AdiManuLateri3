package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.CommonActivity.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                MainPageData("Adult TV Shows - Rating Tertinggi", "1:6:adult"),
                MainPageData("Adult TV Shows - Sesuai Abjad (A-Z)", "1:3:adult"),
                MainPageData("Adult TV Shows - Terlama (Oldest)", "1:2:adult"),
                MainPageData("All Collections - Baru Ditambahkan", "-1:1:adult"),
                MainPageData("All Collections - Paling Sering Ditonton", "-1:5:adult"),
                MainPageData("All Collections - Rekomendasi Terbaik", "-1:6:adult"),
                MainPageData("All Collections - Arsip Lengkap (A-Z)", "-1:3:adult"),
                MainPageData("All Collections - Arsip Lama", "-1:2:adult")
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
            val itemImage = this.image ?: this.poster ?: ""

            val href = "$mainUrl/film/$itemSlug"
            val posterUrl = if (itemImage.isNotEmpty()) {
                if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
            } else {
                ""
            }

            return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
                this.posterUrl = posterUrl
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
            val fullHtml = response.text

            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            // Bersihkan judul dari tahun, misal "Eva (2021)" menjadi "Eva"
            val cleanTitle = title.replace(Regex("""\(\d{4}\)"""), "").trim() 
            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
            
            // Coba scrape ID sederhana
            val scrapedImdbId = Regex("""tt\d{7,8}""").find(fullHtml)?.value

            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie
            val cinemetaType = if (type == TvType.Movie) "movie" else "series"

            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url
            
            val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
                val rTitle = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
                val rImage = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src") ?: ""
                val rHref = it.selectFirst("a")?.attr("href") ?: ""
                if (rTitle.isNotEmpty() && rHref.isNotEmpty()) {
                    newMovieSearchResponse(rTitle, rHref, TvType.Movie) {
                        this.posterUrl = if (rImage.isNotEmpty() && !rImage.startsWith("http")) mainUrl + rImage else rImage
                    }
                } else null
            }

            if (type == TvType.TvSeries) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val episodeText = it.text().trim()
                    val episodeHref = it.attr("href")
                    val episodeNum = Regex("""(\d+)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()

                    if (episodeHref.isNotEmpty()) {
                        // PASSING JUDUL DAN TAHUN KE LINKDATA
                        val data = LinkData(
                            url = episodeHref, 
                            imdbId = scrapedImdbId, 
                            season = null, 
                            episode = episodeNum,
                            title = cleanTitle,
                            year = year,
                            type = cinemetaType
                        ).toJson()
                        
                        newEpisode(data) {
                            this.name = "Episode ${episodeNum ?: episodeText}"
                            this.episode = episodeNum
                        }
                    } else null
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.year = year; this.posterUrl = poster; this.plot = description; this.tags = genre; this.recommendations = recs
                }
            } else {
                // PASSING JUDUL DAN TAHUN KE LINKDATA
                val data = LinkData(
                    url = videoHref, 
                    imdbId = scrapedImdbId, 
                    season = null, 
                    episode = null,
                    title = cleanTitle,
                    year = year,
                    type = cinemetaType
                ).toJson()
                
                return newMovieLoadResponse(title, url, TvType.Movie, data) {
                    this.year = year; this.posterUrl = poster; this.plot = description; this.tags = genre; this.recommendations = recs
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val linkData = try { parseJson<LinkData>(data) } catch (e: Exception) { LinkData(data) }
            val url = linkData.url
            
            // --- LOGIKA PENCARIAN ID OTOMATIS (ULTIMATE FIX) ---
            CoroutineScope(Dispatchers.IO).launch {
                var finalImdbId = linkData.imdbId

                // Jika ID tidak ditemukan saat scraping (null), cari via Cinemeta
                if (finalImdbId.isNullOrBlank() && !linkData.title.isNullOrBlank()) {
                    finalImdbId = AdiDewasaSubtitles.getImdbIdFromCinemeta(
                        linkData.title, 
                        linkData.year, 
                        linkData.type ?: "movie"
                    )
                }

                if (!finalImdbId.isNullOrBlank()) {
                    showToast("ID ditemukan: $finalImdbId. Memuat Subtitle...")
                    AdiDewasaSubtitles.invokeSubtitleAPI(finalImdbId, linkData.season, linkData.episode, subtitleCallback)
                    AdiDewasaSubtitles.invokeWyZIESUBAPI(finalImdbId, linkData.season, linkData.episode, subtitleCallback)
                } else {
                    // showToast("Gagal menemukan ID. Subtitle mungkin tidak tersedia.")
                }
            }
            // ----------------------------------------------------

            val doc = app.get(url).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
            
            val resJson = JSONObject(app.get(signedUrl).text)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            val qualities = videoSource.keys().asSequence().toList().sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull() ?: return false
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(newExtractorLink(name, name, bestQualityUrl))
                
                // Native Subtitles (jika ada di player asli)
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        subtitleCallback(newSubtitleFile("English (Source)", mainUrl + subUrl))
                    }
                }
                return true
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }
}
