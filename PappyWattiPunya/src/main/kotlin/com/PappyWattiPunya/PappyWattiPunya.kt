package com.PappyWattiPunya

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class PappyWattiPunya : MainAPI() {
    override var mainUrl              = "https://mangoporn.net"
    override var name                 = "PappyWattiPunya"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    // Menggunakan User-Agent Android agar trafik terlihat seperti HP biasa
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Referer" to "$mainUrl/",
        "Connection" to "keep-alive" // Meminta server menjaga koneksi tetap terbuka
    )

    override val mainPage = mainPageOf(
        "genres/porn-movies" to "Latest Release",
        "genre/russian" to "Russian",
        "studios/brazzers" to "Brazzers",
        "studios/bang-bros-productions" to "Bang Bros",
        "studios/evil-angel" to "Evil Angle",
        "studios/hustler" to "Hustler",
        "studios/devils-film" to "Devil Film",
        "studios/reality-kings" to "Reality Kings",
        "genre/family-roleplay" to "Family Roleplay",
        "genre/parody" to "Parody",
        "genre/18-teens" to "18+ Teens",
        "genre/anal" to "Anal",
        "genre/big-boobs" to "Big Boobs",
        "genre/blondes" to "Blondes",
        "genre/blowjobs" to "Blowjobs",
        "genre/lesbian" to "Lesbian",
        "genre/deep-throat" to "Deep Throat",
        "genre/cumshots" to "Cumshots",
        "genre/facials" to "Facials",
        "genre/bdsm" to "BDSM",
        "genre/threesomes" to "Threesomes",
        "genre/gangbang" to "Gangbang",
        "genre/redheads" to "Red Heads",
        "genre/squirting" to "Squirting",
        "genre/milf" to "MILF",
        "genre/asian" to "Asian",
        "genre/big-butt" to "Big Butt",
        "genre/big-cock" to "Big Cock"
    )

    // Fungsi bantuan untuk request dengan timeout lebih lama
    private suspend fun request(url: String): org.jsoup.nodes.Document {
        return app.get(
            url, 
            headers = commonHeaders, 
            timeout = 30 // Timeout diperpanjang jadi 30 detik
        ).document
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Menggunakan fungsi request custom
        val document = request("$mainUrl/${request.data}/page/$page")
        val home = document.select("div.items > article")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div h3").text()
        val href      = fixUrl(this.select("div h3 a").attr("href"))
        val posterUrl = this.select("div.poster > img").attr("data-wpfc-original-src")
        
        val finalPoster = if (!posterUrl.contains(".jpg") && posterUrl.length < 5) {
             this.select("div.poster > img").attr("src")
        } else {
             posterUrl
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = finalPoster
        }
    }

    private fun Element.toSearchingResult(): SearchResponse {
        val title = this.select("div.details a").text()
        val href = fixUrl(this.select("div.image a").attr("href"))
        val posterUrl = this.select("div.image img").attr("src")
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..2) {
            // Menggunakan fungsi request custom dengan timeout
            val document = request("${mainUrl}/page/$i/?s=$query")

            val results = document.select("article")
                .mapNotNull { it.toSearchingResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        // Menggunakan fungsi request custom dengan timeout
        val document = request(url)

        val title = document.selectFirst("div.data > h1")?.text().toString()
        val poster = document.selectFirst("div.poster > img")?.attr("data-wpfc-original-src")?.trim().toString()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val recommendations =
            document.select("ul.videos.related >  li").map {
                val recomtitle = it.selectFirst("div.video > a")?.attr("title")?.trim().toString()
                val recomhref = it.selectFirst("div.video > a")?.attr("href").toString()
                val recomposterUrl = it.select("div.video > a > div > img").attr("src")
                val recomposter="https://javdoe.sh$recomposterUrl"
                newAnimeSearchResponse(recomtitle, recomhref, TvType.NSFW) {
                    this.posterUrl = recomposter
                }
            }
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
            this.recommendations=recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Menggunakan request custom
        val document = request(data)
        document.select("div#pettabs > ul a").map {
            val link=it.attr("href")
            loadExtractor(link,subtitleCallback, callback)
        }
        return true
    }
}
