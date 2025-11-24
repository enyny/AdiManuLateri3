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
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- KONFIGURASI API SUBSOURCE ---
    private val subSourceApiUrl = "https://api.subsource.net/api/v1"
    private val subSourceApiKey = "sk_a607958631df470389e6c54d80a2725fb7707d591abf80ed7c5b388854898a94" // API Key Kamu

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

    // --- BAGIAN 3: PENCARIAN FILM ---
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

    // --- BAGIAN 4: LOAD DETAIL (FIX TAHUN FINAL) ---
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        
        val rawTitle = doc.selectFirst("div.right-info h1, h1.title")?.text()?.trim() ?: "Unknown"
        
        // Logika Tahun (Regex)
        var year: Int? = null
        val yearInTitle = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        if (yearInTitle != null) {
            year = yearInTitle
        } 
        if (year == null) {
            val infoText = doc.select("div.right-info, .movie-info").text()
            val releaseRegex = Regex("""Released\s*(?:at)?\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE)
            val dateRegex = Regex("""(\d{4})-\d{2}-\d{2}""")
            year = releaseRegex.find(infoText)?.groupValues?.get(1)?.toIntOrNull() 
                ?: dateRegex.find(infoText)?.groupValues?.get(1)?.toIntOrNull()
        }

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
                this.posterUrl = poster; this.plot = desc; this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, dataPayload) { 
                this.posterUrl = poster; this.plot = desc; this.year = year
            }
        }
    }

    // --- BAGIAN 5: SUBSOURCE API IMPLEMENTATION (NEW!) ---
    private suspend fun fetchSubSource(rawTitle: String, subtitleCallback: (SubtitleFile) -> Unit) {
        val cleanTitle = rawTitle.replace(Regex("""\(\d{4}\)|Episode\s*\d+|Season\s*\d+"""), "")
            .replace(Regex("""[^a-zA-Z0-9 ]"""), " ").trim().replace(Regex("""\s+"""), " ")

        // Header wajib dengan API Key
        val headers = mapOf(
            "X-API-Key" to subSourceApiKey,
            "Accept" to "application/json"
        )

        try {
            // 1. SEARCH MOVIE VIA API
            val searchUrl = "$subSourceApiUrl/movies/search?query=${cleanTitle.replace(" ", "%20")}"
            val searchRes = app.get(searchUrl, headers = headers).text
            
            // Parsing Manual JSON Response (Search)
            val searchJson = JSONObject(searchRes)
            val results = searchJson.optJSONArray("results") ?: searchJson.optJSONArray("data")
            
            if (results != null && results.length() > 0) {
                // Ambil ID film pertama yang cocok
                val firstMovie = results.getJSONObject(0)
                val movieId = firstMovie.optString("id") // atau "movie_id" tergantung respon API
                
                if (movieId.isNotEmpty()) {
                    // 2. GET SUBTITLES LIST VIA API
                    // Biasanya ada endpoint /subtitles?movie_id=XYZ atau /movies/{id}
                    val detailUrl = "$subSourceApiUrl/movies/$movieId"
                    val detailRes = app.get(detailUrl, headers = headers).text
                    val detailJson = JSONObject(detailRes)
                    
                    // Asumsi struktur: { data: { ..., subtitles: [...] } } atau langsung list
                    val movieData = detailJson.optJSONObject("data") ?: detailJson
                    val subtitles = movieData.optJSONArray("subtitles") 
                        ?: detailJson.optJSONArray("subtitles")

                    if (subtitles != null) {
                        for (i in 0 until subtitles.length()) {
                            val sub = subtitles.getJSONObject(i)
                            val lang = sub.optString("language", "en") // id, en, etc
                            val langName = sub.optString("lang_name", sub.optString("display", "Unknown"))
                            
                            // 3. FILTER INDONESIAN ONLY
                            if (lang.equals("id", true) || lang.equals("indonesian", true) || langName.contains("Indo", true)) {
                                val downloadUrl = sub.optString("url") // Link download langsung
                                    .ifEmpty { "$subSourceApiUrl/subtitles/${sub.optString("id")}/download" } // Atau via endpoint download
                                
                                val releaseName = sub.optString("release_name", "SubSource API")
                                
                                subtitleCallback(newSubtitleFile("Indonesia ($releaseName)", downloadUrl))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: Jika API Limit habis atau error, kode tidak akan crash
             e.printStackTrace()
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

        // Panggil API SubSource
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
