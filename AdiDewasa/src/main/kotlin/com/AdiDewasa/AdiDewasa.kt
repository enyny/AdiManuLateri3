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

    // Helper untuk memperbaiki URL gambar agar tidak rusak (double slash atau missing slash)
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
            val posterUrl = fixImgUrl(itemImage)

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
            
            // --- FIX 1: Robust Selector (Menggunakan Meta sebagai backup) ---
            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() 
                ?: doc.selectFirst("meta[property='og:title']")?.attr("content") 
                ?: "Unknown Title"

            val poster = fixImgUrl(
                doc.selectFirst("meta[property='og:image']")?.attr("content") 
                ?: doc.selectFirst("div.poster img")?.attr("src")
            )

            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() 
                ?: doc.selectFirst("meta[property='og:description']")?.attr("content") 
                ?: ""

            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

            // Deteksi Series vs Movie
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list, .episode-item").isNotEmpty()
            val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie
            
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url

            val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
                val rTitle = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
                val rImage = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src")
                val rHref = it.selectFirst("a")?.attr("href") ?: ""
                
                if (rTitle.isNotEmpty() && rHref.isNotEmpty()) {
                    newMovieSearchResponse(rTitle, rHref, TvType.Movie) {
                        this.posterUrl = fixImgUrl(rImage)
                    }
                } else {
                    null
                }
            }

            if (type == TvType.TvSeries) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val episodeText = it.text().trim()
                    val episodeHref = it.attr("href")
                    
                    // Ekstrak nomor episode lebih aman
                    val episodeNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""^(\d+)$""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()

                    if (episodeHref.isNotEmpty()) {
                        newEpisode(episodeHref) {
                            this.name = if(episodeNum != null) "Episode $episodeNum" else episodeText
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
            // --- FIX 2: Pencarian Script yang lebih luas ---
            // Mencari script apapun yang mengandung "signedUrl"
            val script = doc.select("script").find { it.html().contains("signedUrl") }?.html() ?: return false
            
            // --- FIX 3: Robust Regex ---
            // Menangkap 'signedUrl' baik menggunakan kutip satu (') maupun kutip dua (")
            // Juga menangani spasi yang mungkin bervariasi
            val signedUrlMatch = Regex("""signedUrl\s*=\s*['"]([^'"]+)['"]""").find(script)
            
            val signedUrlRaw = signedUrlMatch?.groupValues?.get(1) ?: return false
            val signedUrl = signedUrlRaw.replace("\\/", "/") // Bersihkan escape slash
            
            val res = app.get(signedUrl).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            // Mengambil kualitas tertinggi (biasanya angka terbesar seperti 1080)
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            
            // Loop untuk mencari link yang valid (jika kualitas terbaik gagal/kosong)
            var foundLink = false
            for (key in qualities) {
                val url = videoSource.optString(key)
                if (url.isNotEmpty()) {
                    callback(
                        newExtractorLink(
                            name,
                            "$name ${key}p", // Menambahkan info kualitas di nama
                            url
                        )
                    )
                    foundLink = true
                    
                    // Ambil subtitle hanya dari kualitas terbaik yang ditemukan
                    val subJson = resJson.optJSONObject("sub")
                    subJson?.optJSONArray(key)?.let { array ->
                        for (i in 0 until array.length()) {
                            val subUrl = array.getString(i)
                            // Skip jika subUrl kosong
                            if (subUrl.isNotEmpty()) {
                                subtitleCallback(newSubtitleFile("English ($i)", fixImgUrl(subUrl)))
                            }
                        }
                    }
                    break // Kita ambil 1 kualitas terbaik saja (opsional: hapus break jika mau semua kualitas)
                }
            }
            return foundLink

        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}
