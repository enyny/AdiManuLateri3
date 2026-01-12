package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class LayarKacaProvider : MainAPI() {

    // UPDATE: Menggunakan domain aktif yang kamu temukan
    override var mainUrl = "https://tv7.lk21official.cc" 
    private var seriesUrl = "https://series.lk21.de" // Bisa disesuaikan jika series juga pindah domain
    private var searchurl= "https://search.lk21.party"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terpopuler",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/most-commented/page/" to "Film Dengan Komentar Terbanyak",
        "$seriesUrl/latest-series/page/" to "Series Terbaru",
        "$seriesUrl/series/asian/page/" to "Film Asian Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).documentLarge
        val home = document.select("article figure").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private suspend fun getProperLink(url: String): String {
        if (url.contains("series")) return url
        val res = app.get(url).documentLarge
        return if (res.select("title").text().contains("Nontondrama", true)) {
            res.selectFirst("a#openNow")?.attr("href")
                ?: res.selectFirst("div.links a")?.attr("href")
                ?: url
        } else {
            url
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        val posterheaders= mapOf("Referer" to getBaseUrl(posterUrl))
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterheaders
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterheaders
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan API Search yang masih valid
        val res = app.get("$searchurl/search.php?s=$query").text
        val results = mutableListOf<SearchResponse>()

        try {
            val root = JSONObject(res)
            if (root.has("data")) {
                val arr = root.getJSONArray("data")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val title = item.getString("title")
                    val slug = item.getString("slug")
                    val type = item.getString("type")
                    val posterUrl = "https://poster.lk21.party/wp-content/uploads/"+item.optString("poster")
                    
                    // Memperbaiki URL hasil pencarian agar sesuai mainUrl baru
                    val finalUrl = if (type == "series") "$seriesUrl/$slug" else "$mainUrl/$slug"
                    
                    val searchRes = if (type == "series") {
                        newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) {
                            this.posterUrl = posterUrl
                        }
                    } else {
                        newMovieSearchResponse(title, finalUrl, TvType.Movie) {
                            this.posterUrl = posterUrl
                        }
                    }
                    results.add(searchRes)
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKaca", "Error parsing search json: ${e.message}")
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val fixUrl = getProperLink(url)
        val document = app.get(fixUrl).documentLarge
        val baseurl = fetchURL(fixUrl)
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim().toString()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders= mapOf("Referer" to getBaseUrl(poster))

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.movie-info h1").text().trim()
        )?.groupValues?.get(1)?.toIntOrNull()
        
        val tvType = if (document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = baseurl + it.selectFirst("a")!!.attr("href")
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterheaders
            }
        }

        if (tvType == TvType.TvSeries) {
            val json = document.selectFirst("script#season-data")?.data()
            val episodes = mutableListOf<Episode>()
            if (json != null) {
                try {
                    val root = JSONObject(json)
                    root.keys().forEach { seasonKey ->
                        val seasonArr = root.getJSONArray(seasonKey)
                        for (i in 0 until seasonArr.length()) {
                            val ep = seasonArr.getJSONObject(i)
                            val href = fixUrl("$baseurl/"+ep.getString("slug"))
                            val episodeNo = ep.optInt("episode_no")
                            val seasonNo = ep.optInt("s")
                            episodes.add(
                                newEpisode(href) {
                                    this.name = "Episode $episodeNo"
                                    this.season = seasonNo
                                    this.episode = episodeNo
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LayarKaca", "Error parsing episodes: ${e.message}")
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    // --- BAGIAN PENTING YANG DIPERBAIKI ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("LayarKaca", "LoadLinks URL: $data")
        val document = app.get(data).documentLarge
        val sources = mutableListOf<String>()

        // 1. CARI LINK UTAMA (Dari atribut data-iframe_url yang kamu temukan di screenshot)
        // Ini biasanya link player default yang langsung aktif
        val mainIframe = document.selectFirst("div.main-player")?.attr("data-iframe_url")
        if (!mainIframe.isNullOrEmpty()) {
            Log.d("LayarKaca", "Main iframe found: $mainIframe")
            sources.add(fixUrl(mainIframe))
        }

        // 2. CARI LINK DROPDOWN (Dari <select id="player-select"> yang kamu temukan)
        document.select("select#player-select option").forEach {
            val valUrl = it.attr("value")
            if (valUrl.isNotEmpty()) {
                sources.add(fixUrl(valUrl))
            }
        }

        // 3. CARI LINK LIST LAMA (Backup jika tampilan berubah ke mode desktop lama)
        document.select("ul#player-list > li > a").forEach {
            sources.add(fixUrl(it.attr("href")))
        }
        
        Log.d("LayarKaca", "Total sources found: ${sources.size}")

        // Proses semua link yang ditemukan
        sources.distinct().amap { url ->
            val iframeSrc = url.getIframe()
            val referer = getBaseUrl(url) 
            Log.d("LayarKaca", "Processing iframe: $iframeSrc")
            loadExtractor(iframeSrc, referer, subtitleCallback, callback)
        }
        return true
    }

    private suspend fun String.getIframe(): String {
        // Jika URL itu sendiri sudah mengandung iframe.php atau embed, langsung pakai
        if (this.contains("iframe.php") || this.contains("/embed/")) {
            return this
        }
        
        // Jika tidak, coba buka halamannya dan cari iframe
        return try {
            app.get(this).documentLarge
                .select("div.embed-container iframe")
                .attr("src")
        } catch (e: Exception) {
            this // Return original url on failure
        }
    }

    private suspend fun fetchURL(url: String): String {
        return try {
            val res = app.get(url, allowRedirects = false)
            val href = res.headers["location"]
            if (href != null) {
                val it = URI(href)
                "${it.scheme}://${it.host}"
            } else {
                url
            }
        } catch (e: Exception) {
            url
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("src") -> this.attr("src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> this.attr("src")
        }
    }

    fun getBaseUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return try {
            URI(url).let {
                "${it.scheme}://${it.host}"
            }
        } catch (e: Exception) {
            ""
        }
    }
}
