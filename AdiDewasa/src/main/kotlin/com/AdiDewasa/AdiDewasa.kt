package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
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

    // UPDATE: Menggunakan 15 Kategori yang PASTI ADA isinya.
    // Menghapus filter 'Country' yang menyebabkan daftar kosong.
    // Format Data: "Type:Sort:Adult"
    // Type: 2=Movie, 1=TV, -1=All
    // Sort: 1=Latest, 2=Oldest/Year, 3=A-Z, 5=Most Watched, 6=Top Rated
    override val mainPage: List<MainPageData>
        get() {
            return listOf(
                // --- Kategori FILM (Movies) ---
                MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
                MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
                MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
                MainPageData("Adult Movies - Sesuai Abjad (A-Z)", "2:3:adult"),
                MainPageData("Adult Movies - Klasik & Retro (Oldest)", "2:2:adult"),

                // --- Kategori SERIAL TV (Shows) ---
                MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
                MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
                MainPageData("Adult TV Shows - Rating Tertinggi", "1:6:adult"),
                MainPageData("Adult TV Shows - Sesuai Abjad (A-Z)", "1:3:adult"),
                MainPageData("Adult TV Shows - Terlama (Oldest)", "1:2:adult"),

                // --- Kategori CAMPURAN (All) ---
                MainPageData("All Collections - Baru Ditambahkan", "-1:1:adult"),
                MainPageData("All Collections - Paling Sering Ditonton", "-1:5:adult"),
                MainPageData("All Collections - Rekomendasi Terbaik", "-1:6:adult"),
                MainPageData("All Collections - Arsip Lengkap (A-Z)", "-1:3:adult"),
                MainPageData("All Collections - Arsip Lama", "-1:2:adult")
            )
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            // MODIFIKASI: Kembali ke format 3 parameter yang stabil
            val dataParts = request.data.split(":")
            val type = dataParts.getOrNull(0) ?: "-1"
            val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
            val adultFlag = dataParts.getOrNull(2) ?: "normal"

            val isAdultSection = adultFlag == "adult"

            // PENTING: 'country' di-set ke -1 (Semua Negara) agar tidak ada daftar kosong
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
            val doc = app.get(url).document
            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
            
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie
            
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url

            val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
                val title = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
                val image = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src") ?: ""
                val itemHref = it.selectFirst("a")?.attr("href") ?: ""
                
                if (title.isNotEmpty() && itemHref.isNotEmpty()) {
                    newMovieSearchResponse(title, itemHref, TvType.Movie) {
                        this.posterUrl = if (image.isNotEmpty()) {
                            if (image.startsWith("http")) image else mainUrl + image
                        } else {
                            ""
                        }
                    }
                } else {
                    null
                }
            }

            if (type == TvType.TvSeries) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val episodeText = it.text().trim()
                    val episodeHref = it.attr("href")
                    val episodeNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""(\d+)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()

                    if (episodeHref.isNotEmpty()) {
                        newEpisode(episodeHref) {
                            this.name = "Episode ${episodeNum ?: episodeText}"
                            this.episode = episodeNum
                        }
                    } else {
                        null
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.year = year
                    this.tags = genre
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recs
                }
            } else {
                return newMovieLoadResponse(title, url, TvType.Movie, videoHref) {
                    this.year = year
                    this.tags = genre
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recs
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
            val doc = app.get(data).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return false
            
            val res = app.get(signedUrl).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull() ?: return false
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(
                    newExtractorLink(
                        name,
                        name,
                        bestQualityUrl
                    )
                )
                
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        subtitleCallback(newSubtitleFile("English", mainUrl + subUrl))
                    }
                }
                
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}
