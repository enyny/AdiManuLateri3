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

    // Homepage menggunakan kategori dari Dramafull
    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Latest Movies",
        "$mainUrl/popular" to "Popular",
        "$mainUrl/genre/romance" to "Romance",
        "$mainUrl/genre/drama" to "Drama",
        "$mainUrl/genre/action" to "Action"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if(page == 1) request.data else "${request.data}?page=$page"
        val document = app.get(url).document
        
        val home = document.select(".film-item, .item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".title, h3")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val poster = this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src")
        
        return newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val cleanQuery = AdiDewasaHelper.normalizeQuery(query)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "$mainUrl/api/live-search/$encodedQuery"
        
        // Menggunakan API internal Dramafull seperti yang ditemukan di AdiDrakor
        return app.get(url, headers = AdiDewasaHelper.headers).parsedSafe<DramaFullSearchResponse>()?.data?.map {
            newMovieSearchResponse(it.title ?: it.name ?: "", "$mainUrl/film/${it.slug}", TvType.Movie) {
                this.posterUrl = it.image
                this.year = it.year?.toIntOrNull()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = AdiDewasaHelper.headers).document
        
        val title = document.selectFirst("h1.heading-title")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst(".film-poster img")?.attr("src")
        val desc = document.selectFirst(".description, .film-desc")?.text()?.trim()
        val year = Regex("\\d{4}").find(document.select(".film-info").text())?.value?.toIntOrNull()
        
        // Cek apakah ini series (punya episode)
        val episodes = document.select(".episode-list a, .episode-item a").mapNotNull {
            val epNum = Regex("(\\d+)").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
            val href = it.attr("href")
            if (epNum == null) return@mapNotNull null
            
            newEpisode(
                LinkData(
                    url = fixUrl(href),
                    title = title,
                    year = year,
                    season = 1, // Default season 1 karena Dramafull jarang misah season
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
            // Kalau Movie
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
            // 1. Sumber Utama: Dramafull (Direct)
            {
                invokeAdiDewasaDirect(res.url, callback, subtitleCallback)
            },
            // 2. Extractor Tambahan (Menggunakan Judul & Tahun dari Dramafull)
            {
               invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
            },
            {
               invokeVidsrc(null, res.season, res.episode, subtitleCallback, callback) // Vidsrc sering butuh IMDB, ini fallback tanpa IMDB
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
            // Note: Beberapa extractor mungkin butuh TMDB ID yang akurat. 
            // Karena kita pakai Dramafull, TMDB ID mungkin null, jadi beberapa extractor 
            // mungkin gagal atau hanya mencari berdasarkan query.
        )

        return true
    }
}
