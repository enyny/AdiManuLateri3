package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink // PERBAIKAN: Ditambahkan
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // 1. TAMPILAN HALAMAN UTAMA (KATEGORI DRAMAFULL ASLI)
    override val mainPage: List<MainPageData>
        get() {
            return listOf(
                MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
                MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
                MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
                MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
                MainPageData("All Collections - Rekomendasi", "-1:6:adult"),
                MainPageData("All Collections - Arsip Lengkap", "-1:3:adult")
            )
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val dataParts = request.data.split(":")
        val type = dataParts.getOrNull(0) ?: "-1"
        val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
        val isAdult = dataParts.getOrNull(2) == "adult"
        
        // Request ke API Dramafull
        val jsonPayload = """{
            "page": $page,
            "type": "$type",
            "country": -1,
            "sort": $sort,
            "adult": true,
            "adultOnly": $isAdult,
            "genres": [],
            "keyword": ""
        }""".trimIndent()

        val response = app.post("$mainUrl/api/filter", requestBody = jsonPayload.toRequestBody("application/json".toMediaType())).parsedSafe<HomeResponse>()
        
        val list = response?.data?.mapNotNull { 
            newMovieSearchResponse(it.title ?: it.name ?: "Unknown", "$mainUrl/film/${it.slug}", TvType.Movie) {
                this.posterUrl = if(it.image?.startsWith("http") == true) it.image else "$mainUrl${it.image}"
            }
        } ?: emptyList()

        // PERBAIKAN DI SINI: List harus dibungkus dalam HomePageList
        return newHomePageResponse(HomePageList(request.name, list), hasNext = response?.nextPageUrl != null)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/live-search/$query"
        return app.get(url).parsedSafe<ApiSearchResponse>()?.data?.mapNotNull {
             newMovieSearchResponse(it.title ?: it.name ?: "Unknown", "$mainUrl/film/${it.slug}", TvType.Movie) {
                this.posterUrl = if(it.image?.startsWith("http") == true) it.image else "$mainUrl${it.image}"
            }
        } ?: emptyList()
    }

    // 2. MEMUAT DETAIL FILM (SCRAPING DRAMAFULL)
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = AdiDewasaHelper.headers).document
        val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val desc = doc.selectFirst("div.right-info p.summary-content")?.text()
        val yearStr = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)
        val year = yearStr?.toIntOrNull()
        
        // DATA PENTING untuk dibawa ke loadLinks
        val data = LinkData(title, year, url).toJson()

        val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
            val epNum = Regex("""(\d+)""").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(it.attr("href")) {
                this.name = it.text()
                this.episode = epNum
                // Setiap episode membawa data title & year yang sama
                this.data = LinkData(title, year, if(it.attr("href").startsWith("http")) it.attr("href") else "$mainUrl${it.attr("href")}", epNum).toJson()
            }
        }

        if (episodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, data) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        }
    }

    // 3. MEMUAT LINK (HYBRID AGGREGATOR)
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val args = parseJson<LinkData>(data)
        
        // BRIDGE: Cari ID TMDB di background agar extractor lain bisa jalan
        // (Dramafull tidak punya ID TMDB, jadi kita cari berdasarkan judul & tahun)
        val tmdbId = if(args.year != null) AdiDewasaHelper.fetchTmdbId(args.title, args.year, args.episode == null) else null

        runAllAsync(
            // A. SUMBER UTAMA: DRAMAFULL (Langsung scraping halaman)
            {
                try {
                    val doc = app.get(args.url, headers = AdiDewasaHelper.headers).document
                    val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.data()
                    val signedUrl = Regex("""signedUrl\s*=\s*["']([^"']+)["']""").find(script ?: "")?.groupValues?.get(1)?.replace("\\/", "/")
                    if (signedUrl != null) {
                        val json = app.get(signedUrl, referer = args.url, headers = AdiDewasaHelper.headers).text
                        val videoSource = tryParseJson<Map<String, Any>>(json)?.get("video_source") as? Map<String, String>
                        videoSource?.forEach { (q, url) ->
                            // PERBAIKAN: newExtractorLink sudah di-import
                            callback(newExtractorLink(name, "AdiDewasa ($q)", url, INFER_TYPE))
                        }
                        // Subtitles
                        val bestQ = videoSource?.keys?.maxByOrNull { it.toIntOrNull() ?: 0 }
                        if (bestQ != null) {
                             val subJson = tryParseJson<Map<String, Any>>(json)?.get("sub") as? Map<String, Any>
                             (subJson?.get(bestQ) as? List<String>)?.forEach { 
                                 subtitleCallback(SubtitleFile("English", if(it.startsWith("http")) it else "$mainUrl$it"))
                             }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },

            // B. SUMBER CADANGAN 1: Berbasis Judul (Tidak butuh TMDB ID)
            { AdiDewasaExtractor.invokeAdimoviebox(args.title, args.year, 1, args.episode, callback) },
            { AdiDewasaExtractor.invokeIdlix(args.title, args.year, 1, args.episode, callback) },
            
            // C. SUMBER CADANGAN 2: Berbasis TMDB ID (Butuh Bridge tadi)
            { if(tmdbId != null) AdiDewasaExtractor.invokeVidlink(tmdbId, 1, args.episode, callback) },
            { if(tmdbId != null) AdiDewasaExtractor.invokeVidsrc(tmdbId, 1, args.episode, callback) },
            { if(tmdbId != null) AdiDewasaExtractor.invokeMapple(tmdbId, 1, args.episode, callback) }
        )
        return true
    }

    data class LinkData(val title: String, val year: Int?, val url: String, val episode: Int? = null)
}
