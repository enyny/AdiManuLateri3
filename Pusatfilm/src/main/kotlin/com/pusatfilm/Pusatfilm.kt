package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

class Pusatfilm : MainAPI() {

    override var mainUrl = "https://pusatfilm21.online"
    override var name = "Pusatfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "film-terbaru/page/%d/" to "Film Terbaru",
        "trending/page/%d/" to "Film Trending",
        "genre/action/page/%d/" to "Film Action",
        "series-terbaru/page/%d/" to "Series Terbaru",
        "drama-korea/page/%d/" to "Drama Korea",
        "west-series/page/%d/" to "West Series",
        "drama-china/page/%d/" to "Drama China",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        val quality = this.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
        
        return if (quality.isEmpty()) {
            val episode = Regex("Episode\\s?([0-9]+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    // --- BAGIAN INI YANG DIPERBAIKI (Fungsi Load) ---
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Mengambil data dasar
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst("div.gmr-poster img")?.getImageAttr()?.fixImageQuality()
        val tags = document.select("div.gmr-movie-genre a").map { it.text() }
        val year = document.selectFirst("div.gmr-movie-date a")?.text()?.toIntOrNull()
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
        val rating = document.selectFirst("div.gmr-movie-rating span.gmr-rating-score")?.text()?.toRatingInt()

        // Cek apakah ini Series atau Movie
        val isSeries = document.select("div.vid-episodes").isNotEmpty() || 
                       document.select("div.gmr-listseries").isNotEmpty()

        return if (isSeries) {
            val episodes = document.select("div.vid-episodes a, div.gmr-listseries a").mapNotNull { eps ->
                val href = fixUrl(eps.attr("href"))
                val name = eps.attr("title")
                val episodeMatch = Regex("Episode\\s*(\\d+)").find(name)
                val episode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                val seasonMatch = Regex("Season\\s*(\\d+)").find(name)
                val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = episode
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.rating = rating
            }
        } else {
            // Logic untuk Movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.rating = rating
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Logic untuk mengambil link streaming (KotakAjaib, dll)
        document.select("ul#dropdown-server li a").forEach {
            val serverUrl = base64Decode(it.attr("data-frame"))
            loadExtractor(serverUrl, data, subtitleCallback, callback)
        }
        
        // Fallback untuk iframe biasa jika dropdown tidak ada
        document.select("iframe").forEach {
            val src = it.attr("src")
            if (src.contains("kotakajaib") || src.contains("google")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }
}
