package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.nodes.Document
import kotlin.math.abs

class AdiDewasa : TmdbProvider() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- MAIN PAGE CONFIGURATION ---
    override val mainPage: List<MainPageData>
        get() {
            return listOf(
                MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
                MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
                MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
                MainPageData("Adult Movies - Sesuai Abjad (A-Z)", "2:3:adult"),
                MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
                MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
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
        val itemTitle = this.title ?: this.name ?: return null
        val itemSlug = this.slug ?: return null
        val itemImage = this.image ?: this.poster ?: ""
        val href = "$mainUrl/film/$itemSlug"
        val posterUrl = if (itemImage.isNotEmpty()) {
            if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
        } else { "" }

        return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    // --- HYBRID LOAD LOGIC ---
    // PERBAIKAN UTAMA: Mengembalikan LoadResponse? (Nullable) untuk mengatasi "Not Implemented"
    override suspend fun load(url: String): LoadResponse? {
        // Ambil dokumen asli dari dramafull.cc terlebih dahulu
        val doc = try {
            app.get(url).document
        } catch (e: Exception) {
            // Jika gagal load URL utama, kembalikan null agar CS menangani error
            return null
        }

        // Cek apakah ini TV Series (TMDb mapping untuk TV Series rumit, jadi kita skip dan pakai manual)
        val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
        if (hasEpisodes) {
            return loadManual(url, doc, TvType.TvSeries)
        }

        // --- BLOK PENCARIAN TMDB (SAFE MODE) ---
        try {
            // 1. Ambil informasi dasar untuk pencarian
            val rawTitle = doc.selectFirst("div.right-info h1, h1.title")?.text()?.trim() ?: "Unknown"
            
            // 2. Proses Judul dan Tahun
            val yearRegex = Regex("""\((\d{4})\)""")
            val yearMatch = yearRegex.find(rawTitle)
            val cleanTitle = rawTitle.replace(yearRegex, "").trim()
            val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

            // 3. Cari di TMDb dengan Safety Check
            // Kita bungkus search ini karena film dewasa sering tidak ada di TMDb
            val tmdbSearch = this.search(cleanTitle)?.firstOrNull { 
                val resYear = (it as? MovieSearchResponse)?.year
                if (year != null && resYear != null) {
                    abs(resYear - year) <= 1 
                } else {
                    true
                }
            }

            // 4. Jika ketemu di TMDb, gunakan metadatanya
            if (tmdbSearch != null && tmdbSearch.url.isNotEmpty()) {
                val tmdbLoad = super.load(tmdbSearch.url) as? MovieLoadResponse
                
                if (tmdbLoad != null) {
                    // Berhasil ambil data TMDb! Kembalikan response cantik.
                    return newMovieLoadResponse(tmdbLoad.name, url, TvType.Movie, url) { // 'data' diisi url asli
                        this.posterUrl = tmdbLoad.posterUrl
                        this.backgroundPosterUrl = tmdbLoad.backgroundPosterUrl
                        this.year = tmdbLoad.year
                        this.plot = tmdbLoad.plot
                        this.tags = tmdbLoad.tags
                        this.score = tmdbLoad.score
                        this.actors = tmdbLoad.actors
                        this.recommendations = tmdbLoad.recommendations
                        this.duration = tmdbLoad.duration
                        this.comingSoon = tmdbLoad.comingSoon
                    }
                }
            }
        } catch (e: Exception) {
            // Jika terjadi error SAAT mencari ke TMDb (misal timeout atau parse error),
            // JANGAN CRASH. Lanjut saja ke loadManual di bawah.
            e.printStackTrace()
        }

        // --- FALLBACK KE MANUAL ---
        // Jika kode sampai sini, berarti film tidak ada di TMDb atau error.
        return loadManual(url, doc, TvType.Movie)
    }

    // --- MANUAL LOAD (FALLBACK & TV SERIES) ---
    private suspend fun loadManual(url: String, doc: Document, type: TvType): LoadResponse {
        val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
        
        // Rekomendasi
        val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
            val recTitle = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
            val recImg = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src") ?: ""
            val recHref = it.selectFirst("a")?.attr("href") ?: ""
            
            if (recTitle.isNotEmpty() && recHref.isNotEmpty()) {
                newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                    this.posterUrl = if (recImg.startsWith("http")) recImg else mainUrl + recImg
                }
            } else null
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
                } else null
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.year = year
                this.tags = genre
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recs
            }
        } else {
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url
            
            return newMovieLoadResponse(title, url, TvType.Movie, videoHref) {
                this.year = year
                this.tags = genre
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recs
            }
        }
    }

    // --- EXTRACTOR ---
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
            
            var found = false
            qualities.forEach { qualityKey ->
                val videoUrl = videoSource.optString(qualityKey)
                if (videoUrl.isNotEmpty()) {
                    callback(
                        newExtractorLink(
                            name,
                            "$name $qualityKey", 
                            videoUrl,
                            INFER_TYPE 
                        ) {
                            this.referer = mainUrl
                            this.quality = qualityKey.toIntOrNull() ?: 0
                        }
                    )
                    found = true
                }
            }

            val bestQualityKey = qualities.firstOrNull()
            if (bestQualityKey != null) {
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        val fixedSubUrl = if (subUrl.startsWith("http")) subUrl else mainUrl + subUrl
                        subtitleCallback(newSubtitleFile("English", fixedSubUrl))
                    }
                }
            }
            
            return found
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
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
}
