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
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- BAGIAN 1: MENU UTAMA ---
    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Movies - Terbaru", "2:1:adult"),
            MainPageData("Movies - Populer", "2:5:adult"),
            MainPageData("TV Shows - Terbaru", "1:1:adult"),
            MainPageData("TV Shows - Populer", "1:5:adult")
        )

    private fun fixImgUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return if (url.startsWith("http")) url else "$mainUrl${if (url.startsWith("/")) "" else "/"}$url"
    }

    // --- BAGIAN 2: LOAD HALAMAN DEPAN ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val dataParts = request.data.split(":")
            val type = dataParts.getOrNull(0) ?: "-1"
            val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
            
            val jsonPayload = """{"page":$page,"type":"$type","country":-1,"sort":$sort,"adult":true,"adultOnly":true,"ignoreWatched":false,"genres":[],"keyword":""}"""
            
            val response = app.post("$mainUrl/api/filter", requestBody = jsonPayload.toRequestBody("application/json".toMediaType()))
            val homeResponse = response.parsedSafe<HomeResponse>() ?: return newHomePageResponse(emptyList(), false)
            
            val searchResults = homeResponse.data?.mapNotNull { item ->
                val title = item.title ?: item.name ?: return@mapNotNull null
                val slug = item.slug ?: return@mapNotNull null
                newMovieSearchResponse(title, "$mainUrl/film/$slug", TvType.Movie) {
                    this.posterUrl = fixImgUrl(item.image ?: item.poster)
                }
            } ?: emptyList()

            return newHomePageResponse(HomePageList(request.name, searchResults), homeResponse.nextPageUrl != null)
        } catch (e: Exception) {
            return newHomePageResponse(emptyList(), false)
        }
    }

    // --- BAGIAN 3: PENCARIAN ---
    override suspend fun search(query: String): List<SearchResponse>? {
        return try {
            app.get("$mainUrl/api/live-search/$query").parsedSafe<ApiSearchResponse>()?.data?.mapNotNull { 
                val title = it.title ?: it.name ?: return@mapNotNull null
                newMovieSearchResponse(title, "$mainUrl/film/${it.slug}", TvType.Movie) {
                    this.posterUrl = fixImgUrl(it.image ?: it.poster)
                }
            }
        } catch (e: Exception) { null }
    }

    // --- BAGIAN 4: LOAD DETAIL (LOGIKA TAHUN PERBAIKAN FINAL) ---
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        
        // 1. Ambil Judul Mentah
        val rawTitle = doc.selectFirst("div.right-info h1, h1.title")?.text()?.trim() ?: "Unknown"
        
        // 2. DETEKSI TAHUN (PRIORITAS BERTINGKAT)
        var year: Int? = null

        // Cek A: Cari di Judul -> "Gang Rape (2010)"
        val yearInTitle = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        if (yearInTitle != null) {
            year = yearInTitle
        } 
        
        // Cek B: Jika Judul kosong, cari di teks info
        // Menangani: "Released at: 2015-09-10" atau "Released: 2015"
        if (year == null) {
            val infoText = doc.select("div.right-info, .movie-info").text()
            
            val releaseRegex = Regex("""Released\s*(?:at)?\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE)
            val dateRegex = Regex("""(\d{4})-\d{2}-\d{2}""") // Cari format tanggal YYYY-MM-DD
            
            year = releaseRegex.find(infoText)?.groupValues?.get(1)?.toIntOrNull() 
                ?: dateRegex.find(infoText)?.groupValues?.get(1)?.toIntOrNull()
        }

        // 3. Bersihkan Judul (Hapus tahun dari string tampilan)
        val title = rawTitle.replace(Regex("""\s*\(\d{4}\)"""), "").trim()

        val poster = fixImgUrl(doc.selectFirst("meta[property='og:image']")?.attr("content"))
        val desc = doc.selectFirst("div.right-info p.summary-content, .description")?.text()?.trim()
        
        val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url
        val dataPayload = "$videoHref|$title" 

        val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
            val epNum = Regex("""Episode\s*(\d+)""").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
            newEpisode("${it.attr("href")}|$title") { 
                this.name = "Episode ${epNum ?: it.text()}"; this.episode = epNum 
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) { 
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, dataPayload) { 
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        }
    }

    // --- BAGIAN 5: SUBTITLE SUBSOURCE (AUTO INDO) ---
    private suspend fun fetchSubSource(rawTitle: String, subtitleCallback: (SubtitleFile) -> Unit) {
        val cleanTitle = rawTitle.replace(Regex("""\(\d{4}\)|Episode\s*\d+|Season\s*\d+"""), "")
            .replace(Regex("""[^a-zA-Z0-9 ]"""), " ") 
            .trim()
            .replace(Regex("""\s+"""), " ")

        val queries = listOf(
            cleanTitle.replace(" ", "+"), 
            cleanTitle.split(" ").take(2).joinToString("+") 
        ).distinct()

        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0.4472.124 Safari/537.36")

        for (q in queries) {
            if (q.length < 3) continue 
            try {
                val searchDoc = app.get("https://subsource.net/search/$q", headers = headers).document
                val firstResult = searchDoc.selectFirst("div.movie-list div.movie-entry a")
                
                if (firstResult != null) {
                    var detailHref = firstResult.attr("href")
                    if (!detailHref.startsWith("http")) detailHref = "https://subsource.net$detailHref"

                    val detailDoc = app.get(detailHref, headers = headers).document
                    val indoItems = detailDoc.select("div.language-container:contains(Indonesian) div.subtitle-item, tr:contains(Indonesian)")
                    
                    if (indoItems.isNotEmpty()) {
                        indoItems.forEach { item ->
                            val dwnUrl = item.selectFirst("a.download-button, a")?.attr("href")
                            if (!dwnUrl.isNullOrEmpty()) {
                                val fullUrl = if (dwnUrl.startsWith("http")) dwnUrl else "https://subsource.net$dwnUrl"
                                val releaseName = item.select("span.release-name").text().trim()
                                subtitleCallback(newSubtitleFile("Indonesia ($releaseName)", fullUrl))
                            }
                        }
                        return 
                    }
                }
            } catch (e: Exception) { }
        }
    }

    // --- BAGIAN 6: PLAYER VIDEO ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val linkUrl = parts[0]
        val titleForSub = parts.getOrNull(1) ?: ""

        if (titleForSub.isNotEmpty()) fetchSubSource(titleForSub, subtitleCallback)

        try {
            val doc = app.get(linkUrl).document
            val script = doc.select("script").find { it.html().contains("signedUrl") }?.html() ?: return false
            val signedUrl = Regex("""signedUrl\s*=\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
            
            val json = JSONObject(app.get(signedUrl).text)
            val videoSource = json.optJSONObject("video_source") ?: return false
            val qualities = videoSource.keys().asSequence().toList().sortedByDescending { it.toIntOrNull() ?: 0 }

            for (key in qualities) {
                val vidUrl = videoSource.optString(key)
                if (vidUrl.isNotEmpty()) {
                    callback(newExtractorLink(name, "$name ${key}p", vidUrl))
                    
                    json.optJSONObject("sub")?.optJSONArray(key)?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val sUrl = arr.getString(i)
                            if (sUrl.isNotEmpty()) {
                                val fixedUrl = if (sUrl.startsWith("http")) sUrl else "$mainUrl$sUrl"
                                val lang = if(fixedUrl.contains("indo")) "Indonesia (Ori)" else "English (Ori)"
                                subtitleCallback(newSubtitleFile(lang, fixedUrl))
                            }
                        }
                    }
                    return true
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }
}
