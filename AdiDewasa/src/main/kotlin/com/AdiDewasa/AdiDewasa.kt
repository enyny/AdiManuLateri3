package com.AdiDewasa

import com.AdiDewasa.AdiDewasaExtractor.invokeAdiDewasaDirect
import com.AdiDewasa.AdiDewasaExtractor.invokeIdlix
import com.AdiDewasa.AdiDewasaExtractor.invokeMapple
import com.AdiDewasa.AdiDewasaExtractor.invokeSuperembed
import com.AdiDewasa.AdiDewasaExtractor.invokeVidfast
import com.AdiDewasa.AdiDewasaExtractor.invokeVidlink
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrccc
import com.AdiDewasa.AdiDewasaExtractor.invokeVixsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeWyzie
import com.AdiDewasa.AdiDewasaExtractor.invokeXprime
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import java.net.URLEncoder

class AdiDewasa : MainAPI() {
    override var name = "AdiDewasa"
    override var mainUrl = "https://dramafull.cc"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Latest Movies",
        "$mainUrl/popular" to "Popular",
        "$mainUrl/genre/romance" to "Romance",
        "$mainUrl/genre/drama" to "Drama",
        "$mainUrl/genre/action" to "Action"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if(page == 1) request.data else "${request.data}?page=$page"
        
        // FIX: Menambahkan headers agar request tidak diblokir
        val document = app.get(url, headers = AdiDewasaHelper.headers).document
        
        // FIX: Menggunakan selector yang lebih umum untuk menangkap berbagai tema
        val home = document.select("div.item, .film-item, article.post-item, li.item, .movie-item").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Mencari judul di berbagai kemungkinan elemen
        val title = this.selectFirst(".title, h3, h2, .entry-title")?.text()?.trim() ?: return null
        
        val href = this.selectFirst("a")?.attr("href") ?: return null
        
        // Mencari poster di berbagai atribut lazy load
        val imgTag = this.selectFirst("img")
        val poster = imgTag?.attr("data-src") 
            ?: imgTag?.attr("data-original") 
            ?: imgTag?.attr("src")
        
        val quality = this.selectFirst(".quality, .ep")?.text()?.trim()

        return newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            this.posterUrl = poster
            // Menambahkan indikator kualitas di pojok poster jika ada
            if(quality != null) {
                addQuality(quality)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val cleanQuery = AdiDewasaHelper.normalizeQuery(query)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "$mainUrl/api/live-search/$encodedQuery"
        
        return app.get(url, headers = AdiDewasaHelper.headers).parsedSafe<DramaFullSearchResponse>()?.data?.map {
            newMovieSearchResponse(it.title ?: it.name ?: "", "$mainUrl/film/${it.slug}", TvType.Movie) {
                this.posterUrl = it.image
                this.year = it.year?.toIntOrNull()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = AdiDewasaHelper.headers).document
        
        val title = document.selectFirst("h1.heading-title, h1.title")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst(".film-poster img, .poster img")?.attr("src")
        val desc = document.selectFirst(".description, .film-desc, .story")?.text()?.trim()
        val year = Regex("\\d{4}").find(document.select(".film-info, .meta").text())?.value?.toIntOrNull()
        
        // Cek apakah ini series (punya episode)
        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val text = it.text()
            val epNum = Regex("(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val href = it.attr("href")
            
            if (epNum == null) return@mapNotNull null
            
            newEpisode(
                LinkData(
                    url = fixUrl(href),
                    title = title,
                    year = year,
                    season = 1,
                    episode = epNum
                )
            ) {
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LinkData(
                url = url,
                title = title,
                year = year
            )) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)

        runAllAsync(
            {
                invokeAdiDewasaDirect(res.url, callback, subtitleCallback)
            },
            {
               invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
            },
            {
               invokeVidsrc(null, res.season, res.episode, subtitleCallback, callback)
            },
            {
               invokeXprime(null, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
            },
            {
               invokeVidfast(null, res.season, res.episode, subtitleCallback, callback)
            },
            {
               invokeVidlink(null, res.season, res.episode, callback)
            },
            {
               invokeMapple(null, res.season, res.episode, subtitleCallback, callback)
            },
            {
               invokeWyzie(null, res.season, res.episode, subtitleCallback)
            },
            {
               invokeVixsrc(null, res.season, res.episode, callback)
            },
            {
               invokeSuperembed(null, res.season, res.episode, subtitleCallback, callback)
            }
        )

        return true
    }
}
