package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- KATEGORI HALAMAN UTAMA ---
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

    // --- LOGIKA HALAMAN UTAMA ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val dataParts = request.data.split(":")
            val type = dataParts.getOrNull(0) ?: "-1"
            val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
            val adultFlag = dataParts.getOrNull(2) ?: "normal"
            val isAdultSection = adultFlag == "adult"

            // Payload JSON untuk API Filter
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
            
            // Menggunakan headers dari Helper agar konsisten
            val response = app.post("$mainUrl/api/filter", requestBody = payload, headers = AdiDewasaHelper.headers)
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

    // Konversi data JSON ke SearchResponse CloudStream
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

    // --- LOGIKA PENCARIAN ---
    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            // Menggunakan Helper untuk normalisasi query sebelum dikirim ke API
            val cleanQuery = AdiDewasaHelper.normalizeQuery(query)
            val url = "$mainUrl/api/live-search/$cleanQuery"
            
            val response = app.get(url, headers = AdiDewasaHelper.headers)
            val searchResponse = response.parsedSafe<ApiSearchResponse>()
            return searchResponse?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) { return null }
    }

    // --- LOGIKA MEMUAT DETAIL FILM/SERIES ---
    override suspend fun load(url: String): LoadResponse {
        try {
            val doc = app.get(url, headers = AdiDewasaHelper.headers).document
            
            // Scraping Metadata
            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
            
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            val cleanTitle = title.replace(Regex("""\(\d{4}\)"""), "").trim()

            // Rekomendasi
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
                            this.season = 1 
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

    // --- LOGIKA MEMUAT LINK VIDEO ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        // Ambil metadata dasar dari halaman untuk keperluan pencarian subtitle
        val doc = app.get(data, headers = AdiDewasaHelper.headers).document
        val pageTitle = doc.selectFirst("h1.title")?.text() ?: ""
        val year = Regex("""\((\d{4})\)""").find(pageTitle)?.groupValues?.get(1)?.toIntOrNull()
        val cleanTitle = pageTitle.replace(Regex("""\(\d{4}\)"""), "").replace(Regex("Episode.*"), "").trim()
        
        val episodeNum = Regex("""Episode\s*(\d+)""").find(pageTitle)?.groupValues?.get(1)?.toIntOrNull()
        val isSeries = episodeNum != null
        val seasonNum = if (isSeries) 1 else null
        val type = if (isSeries) TvType.TvSeries else TvType.Movie

        // Memanggil Extractor untuk menangani video
        // Kita passing judul, tahun, dll untuk keperluan subtitle eksternal di dalam extractor nanti
        AdiDewasaExtractor.invokeAdiDewasa(
            title = cleanTitle,
            year = year,
            season = seasonNum,
            episode = episodeNum,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
        
        // Jalankan pencarian subtitle eksternal (IMDB + OpenSubtitles) secara Asynchronous
        // Kita gunakan helper yang sudah dibuat di Utils
        runAllAsync {
            val imdbId = getImdbIdFromTitle(cleanTitle, year, type)
            if (imdbId != null) {
                invokeSubtitleAPI(imdbId, seasonNum, episodeNum, subtitleCallback)
                invokeWyZIESUBAPI(imdbId, seasonNum, episodeNum, subtitleCallback)
            }
        }

        return true
    }
}
