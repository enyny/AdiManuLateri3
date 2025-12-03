package com.AdiManuLateri3

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

object Lateri3PlayExtractor {
    
    private const val TAG = "Lateri3Play"
    private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
    
    private var cachedDomains: DomainsParser? = null

    // Fallback domains jika GitHub gagal/diblokir
    private val fallbackDomains = DomainsParser(
        moviesdrive = "https://moviesdrive.io",
        hdhub4u = "https://hdhub4u.tienda",
        n4khdhub = "https://4khdhub.com",
        multiMovies = "https://multimovies.cloud",
        bollyflix = "https://bollyflix.boo",
        uhdmovies = "https://uhdmovies.fyi",
        moviesmod = "https://moviesmod.com.in",
        topMovies = "https://topmovies.boo",
        hdmovie2 = "https://hdmovie2.rest",
        vegamovies = "https://vegamovies.rs",
        rogmovies = "https://rogmovies.com",
        luxmovies = "https://luxmovies.org",
        xprime = "https://xprime.tv",
        extramovies = "https://extramovies.bar",
        dramadrip = "https://dramadrip.me",
        toonstream = "https://toonstream.co"
    )

    private suspend fun getDomains(): DomainsParser {
        if (cachedDomains != null) return cachedDomains!!
        return try {
            val response = app.get(DOMAINS_URL).parsedSafe<DomainsParser>()
            if (response != null) {
                cachedDomains = response
                response
            } else {
                fallbackDomains
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat domain dari GitHub, menggunakan fallback: ${e.message}")
            fallbackDomains
        }
    }

    // ================== PROVIDER 1: UHD MOVIES ==================
    suspend fun invokeUhdmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val api = getDomains().uhdmovies
        val searchTitle = title?.replace("-", " ")?.replace(":", " ") ?: return
        val searchUrl = if (season != null) "$api/search/$searchTitle $year" else "$api/search/$searchTitle"

        try {
            val searchRes = app.get(searchUrl)
            val url = searchRes.documentLarge.select("article div.entry-image a")
                .firstOrNull()?.attr("href") ?: return

            val doc = app.get(url).documentLarge
            val seasonPattern = season?.let { "(?i)(S0?$it|Season 0?$it)" }
            val episodePattern = episode?.let { "(?i)(Episode $it)" }

            val selector = if (season == null) "div.entry-content p:matches($year)" 
                           else "div.entry-content p:matches($seasonPattern)"
            val epSelector = if (season == null) "a:matches((?i)(Download))" 
                             else "a:matches($episodePattern)"

            val links = doc.select(selector).mapNotNull {
                it.nextElementSibling()?.select(epSelector)?.attr("href")
            }

            for (link in links) {
                if (link.isBlank()) continue
                val finalLink = if (link.contains("unblockedgames")) bypassHrefli(link) else link
                if (!finalLink.isNullOrBlank()) {
                    loadSourceNameExtractor("UHDMovies", finalLink, "", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "UHD Error: ${e.message}")
        }
    }

    // ================== PROVIDER 2: VEGAMOVIES ==================
    suspend fun invokeVegamovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains().vegamovies
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")?.trim().orEmpty()
        val query = if (season == null) "$fixtitle $year" else "$fixtitle season $season $year"
        val url = "$api/?s=$query"
        
        try {
            val searchDoc = app.get(url, interceptor = CloudflareKiller()).documentLarge
            for (article in searchDoc.select("article h2")) {
                val href = article.selectFirst("a")?.attr("href") ?: continue
                val doc = app.get(href).documentLarge

                // Validasi IMDB
                if (imdbId != null) {
                    val imdbLink = doc.selectFirst("a[href*=\"imdb.com/title/tt\"]")?.attr("href")
                    if (imdbLink != null && !imdbLink.contains(imdbId, true)) continue
                }

                if (season == null) {
                    doc.select("button.dwd-button, button.btn-outline").forEach { btn ->
                        val link = btn.closest("a")?.attr("href") ?: return@forEach
                        val detailDoc = app.get(link).documentLarge
                        detailDoc.select("button.btn-outline").forEach { dlBtn ->
                            val dlLink = dlBtn.closest("a")?.attr("href")
                            if (!dlLink.isNullOrBlank()) {
                                loadSourceNameExtractor("VegaMovies", dlLink, "$api/", subtitleCallback, callback)
                            }
                        }
                    }
                } else {
                    val seasonBlock = doc.select("h4:matches((?i)Season $season), h3:matches((?i)Season $season)")
                    seasonBlock.forEach { block ->
                        val epLinks = block.nextElementSibling()?.select("a:matches((?i)(Episode $episode|Ep\\s*$episode))") ?: return@forEach
                        epLinks.forEach { epLink ->
                            val epUrl = epLink.attr("href")
                            val epDoc = app.get(epUrl).documentLarge
                            epDoc.select("a:matches((?i)(V-Cloud|G-Direct))").forEach { 
                                loadSourceNameExtractor("VegaMovies", it.attr("href"), "$api/", subtitleCallback, callback) 
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VegaMovies Error: ${e.message}")
        }
    }

    // ================== PROVIDER 3: MOVIESMOD ==================
    suspend fun invokeMoviesmod(
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains().moviesmod
        val searchUrl = if (season == null) "$api/search/$imdbId $year" else "$api/search/$imdbId Season $season $year"
        
        try {
            val href = app.get(searchUrl).documentLarge.selectFirst("#content_box article a")?.attr("href") ?: return
            val doc = app.get(href, interceptor = CloudflareKiller()).documentLarge

            if (season == null) {
                doc.select("a.maxbutton-download-links").forEach { 
                    val decoded = base64Decode(it.attr("href").substringAfter("="))
                    val detailDoc = app.get(decoded).documentLarge
                    detailDoc.select("a.maxbutton-fast-server-gdrive").forEach { dl ->
                        val finalUrl = if (dl.attr("href").contains("unblockedgames")) bypassHrefli(dl.attr("href")) else dl.attr("href")
                        if (finalUrl != null) loadSourceNameExtractor("MoviesMod", finalUrl, "$api/", subtitleCallback, callback)
                    }
                }
            } else {
                val seasonBlock = doc.select("div.mod h3:matches((?i)Season $season)")
                seasonBlock.forEach { h3 ->
                    val epLinks = h3.nextElementSibling()?.select("a.maxbutton-episode-links") ?: return@forEach
                    epLinks.forEach { link ->
                        val decoded = base64Decode(link.attr("href").substringAfter("="))
                        val epDoc = app.get(decoded).documentLarge
                        val targetEp = epDoc.select("span strong").firstOrNull { it.text().contains("Episode $episode", true) }
                            ?.parent()?.closest("a")?.attr("href")
                        
                        if (targetEp != null) {
                            val finalUrl = if (targetEp.contains("unblockedgames")) bypassHrefli(targetEp) else targetEp
                            if (finalUrl != null) loadSourceNameExtractor("MoviesMod", finalUrl, "$api/", subtitleCallback, callback)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MoviesMod Error: ${e.message}")
        }
    }

    // ================== PROVIDER 4: MULTIMOVIES ==================
    suspend fun invokeMultimovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains().multiMovies
        val slug = title?.createSlug() ?: return
        val url = if (season == null) "$api/movies/$slug" else "$api/episodes/$slug-$season" + "x$episode"

        try {
            val response = app.get(url, interceptor = CloudflareKiller())
            if (response.code != 200) return
            
            val doc = response.documentLarge
            doc.select("ul#playeroptionsul li").forEach { li ->
                val type = li.attr("data-type")
                val post = li.attr("data-post")
                val nume = li.attr("data-nume")
                
                val ajax = app.post(
                    "$api/wp-admin/admin-ajax.php",
                    data = mapOf("action" to "doo_player_ajax", "post" to post, "nume" to nume, "type" to type),
                    referer = url
                ).parsedSafe<ResponseHash>()
                
                val embedUrl = ajax?.embed_url?.replace("\\", "")?.replace("\"", "")
                if (embedUrl != null && !embedUrl.contains("youtube")) {
                    loadSourceNameExtractor("MultiMovies", embedUrl, "$api/", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "MultiMovies Error: ${e.message}") }
    }

    // ================== PROVIDER 5: RIDOMOVIES ==================
    suspend fun invokeRidomovies(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = "https://ridomovies.tv"
        try {
            val searchRes = app.get("$api/core/api/search?q=$imdbId").parsedSafe<RidoSearch>()
            val slug = searchRes?.data?.items?.find { it.contentable?.tmdbId == tmdbId }?.slug ?: return
            
            val contentId = if (season != null) {
                val epUrl = "$api/tv/$slug/season-$season/episode-$episode"
                app.get(epUrl).text.substringAfterLast("postid\":\"").substringBefore("\"")
            } else {
                slug
            }

            val typePath = if (season == null) "movies" else "episodes"
            val videos = app.get("$api/core/api/$typePath/$contentId/videos").parsedSafe<RidoResponses>()
            
            videos?.data?.forEach { video ->
                val iframe = Jsoup.parse(video.url ?: "").select("iframe").attr("data-src")
                if (iframe.startsWith("https://closeload.top")) {
                    val unpacked = getAndUnpack(app.get(iframe, referer = "$api/").text)
                    val hash = Regex("\\(\"([^\"]+)\"\\);").find(unpacked)?.groupValues?.get(1) ?: ""
                    val m3u8 = base64Decode(base64Decode(hash).reversed()).split("|").getOrNull(1)
                    if (m3u8 != null) {
                        callback.invoke(newExtractorLink("Ridomovies", "Ridomovies", m3u8, ExtractorLinkType.M3U8) {
                            this.referer = "https://closeload.top/"
                        })
                    }
                } else {
                    loadSourceNameExtractor("Ridomovies", iframe, "$api/", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "RidoMovies Error: ${e.message}") }
    }

    // ================== PROVIDER 6: MOVIESDRIVE ==================
    suspend fun invokeMoviesdrive(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        imdbId: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains().moviesdrive
        val query = if (season != null) "$title $season" else "$title $year"
        
        try {
            val search = app.get("$api/?s=$query").documentLarge
            val link = search.select("figure a").firstOrNull()?.attr("href") ?: return
            val doc = app.get(link).documentLarge
            
            val imdbLink = doc.selectFirst("a[href*=\"imdb.com\"]")?.attr("href")
            if (imdbId != null && imdbLink != null && !imdbLink.contains(imdbId)) return

            if (season == null) {
                doc.select("h5 a").forEach { 
                    extractMdrive(it.attr("href")).forEach { url ->
                        processDriveLink(url, "MoviesDrive", subtitleCallback, callback)
                    }
                }
            } else {
                val sBlock = doc.select("h5:matches((?i)Season\\s*0?$season)").first()
                val epUrl = sBlock?.nextElementSibling()?.selectFirst("a")?.attr("href") ?: return
                val epDoc = app.get(epUrl).documentLarge
                val epBlock = epDoc.select("h5:matches((?i)Episode\\s+0?$episode)").first()
                
                var sibling = epBlock?.nextElementSibling()
                while (sibling != null && sibling.tagName() != "hr") {
                    if (sibling.tagName() == "h5") {
                        val href = sibling.selectFirst("a")?.attr("href")
                        if (href != null && !href.contains("Zip", true)) {
                            processDriveLink(href, "MoviesDrive", subtitleCallback, callback)
                        }
                    }
                    sibling = sibling.nextElementSibling()
                }
            }
        } catch (e: Exception) { Log.e(TAG, "MoviesDrive Error: ${e.message}") }
    }

    private suspend fun processDriveLink(url: String, source: String, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) {
        when {
            url.contains("hubcloud") -> HubCloud().getUrl(url, source, sub, cb)
            url.contains("gdlink") -> GDFlix().getUrl(url, source, sub, cb)
            else -> loadSourceNameExtractor(source, url, "", sub, cb)
        }
    }

    // ================== PROVIDER 7 & 8: DOTMOVIES & ROGMOVIES ==================
    suspend fun invokeDotmovies(imdbId: String?, title: String?, year: Int?, season: Int?, episode: Int?, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) {
        val api = getDomains().luxmovies
        invokeWpredis("DotMovies", api, imdbId, title, year, season, episode, sub, cb)
    }

    suspend fun invokeRogmovies(imdbId: String?, title: String?, year: Int?, season: Int?, episode: Int?, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) {
        val api = getDomains().rogmovies
        invokeWpredis("RogMovies", api, imdbId, title, year, season, episode, sub, cb)
    }

    private suspend fun invokeWpredis(sourceName: String, api: String, imdbId: String?, title: String?, year: Int?, season: Int?, episode: Int?, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) {
        val query = if (season == null) "search/$imdbId" else "search/$imdbId season $season"
        try {
            val res = app.get("$api/$query", interceptor = CloudflareKiller())
            val doc = res.documentLarge
            val article = doc.selectFirst("article h3 a") ?: return
            val detailUrl = article.attr("href")
            val detailDoc = app.get(detailUrl, interceptor = CloudflareKiller()).documentLarge

            if (season == null) {
                detailDoc.select("a.maxbutton-download-links").forEach {
                    loadSourceNameExtractor(sourceName, it.attr("href"), "$api/", sub, cb)
                }
            } else {
                val sBlock = detailDoc.select("h3:matches((?i)Season\\s*$season)").first()
                var sibling = sBlock?.nextElementSibling()
                while (sibling != null) {
                    if (sibling.text().contains("Episode $episode", true) || sibling.select("a").text().contains("Episode $episode", true)) {
                        sibling.select("a").forEach { link ->
                            loadSourceNameExtractor(sourceName, link.attr("href"), "$api/", sub, cb)
                        }
                    }
                    sibling = sibling.nextElementSibling()
                }
            }
        } catch (_: Exception) {}
    }

    // ================== PROVIDER 9: HDMOVIE2 ==================
    suspend fun invokeHdmovie2(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = getDomains().hdmovie2
        val slug = title?.createSlug() ?: return
        val url = "$api/movies/$slug-$year"
        
        try {
            val doc = app.get(url).documentLarge
            var post = ""
            var nume = ""
            
            if (episode != null) {
                val epItem = doc.select("ul#playeroptionsul > li").getOrNull(1)
                post = epItem?.attr("data-post") ?: ""
                nume = (episode + 1).toString()
            } else {
                val mvItem = doc.select("ul#playeroptionsul > li").firstOrNull { it.text().contains("v2", true) }
                post = mvItem?.attr("data-post") ?: ""
                nume = mvItem?.attr("data-nume") ?: ""
            }

            if (post.isNotEmpty()) {
                val ajax = app.post(
                    "$api/wp-admin/admin-ajax.php",
                    data = mapOf("action" to "doo_player_ajax", "post" to post, "nume" to nume, "type" to "movie"),
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = url
                ).parsedSafe<ResponseHash>()
                
                val iframe = Jsoup.parse(ajax?.embed_url ?: "").select("iframe").attr("src")
                if (iframe.isNotEmpty()) loadSourceNameExtractor("HDMovie2", iframe, api, subtitleCallback, callback)
            }
        } catch (_: Exception) {}
    }

    // ================== PROVIDER 10: TOPMOVIES ==================
    suspend fun invokeTopMovies(
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = getDomains().topMovies
        val query = if (season == null) "$imdbId $year" else "$imdbId Season $season"
        
        try {
            val search = app.get("$api/search/$query").documentLarge
            val link = search.select("#content_box article a").attr("href")
            if (link.isBlank()) return
            
            val doc = app.get(link).documentLarge
            
            if (season == null) {
                doc.select("a.maxbutton-fast-server-gdrive").forEach {
                    val url = if (it.attr("href").contains("unblockedgames")) bypassHrefli(it.attr("href")) else it.attr("href")
                    if (url != null) loadSourceNameExtractor("TopMovies", url, "$api/", subtitleCallback, callback)
                }
            } else {
                val epLink = doc.select("span strong").firstOrNull { 
                    it.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE)) 
                }?.parent()?.closest("a")?.attr("href")
                
                if (epLink != null) {
                    val url = if (epLink.contains("unblockedgames")) bypassHrefli(epLink) else epLink
                    if (url != null) loadSourceNameExtractor("TopMovies", url, "$api/", subtitleCallback, callback)
                }
            }
        } catch (_: Exception) {}
    }

    // ================== PROVIDER 11: BOLLYFLIX ==================
    suspend fun invokeBollyflix(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val api = getDomains().bollyflix
        val query = if (season != null) "$id $season" else id
        try {
            val search = app.get("$api/search/$query", interceptor = CloudflareKiller()).documentLarge
            val link = search.selectFirst("div > article > a")?.attr("href") ?: return
            val doc = app.get(link).documentLarge
            
            val hTag = if (season == null) "h5" else "h4"
            val sTag = if (season != null) "Season $season" else ""
            
            val entries = doc.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(720p|1080p))")
            
            entries.forEach { entry ->
                val href = entry.nextElementSibling()?.selectFirst("a")?.attr("href") ?: return@forEach
                val token = href.substringAfter("id=", "")
                if (token.isNotEmpty()) {
                    val encoded = app.get("https://blog.finzoox.com/?id=$token").text.substringAfter("link\":\"").substringBefore("\"};")
                    val decoded = base64Decode(encoded)
                    
                    if (season == null) {
                        processBollyLink(decoded, subtitleCallback, callback)
                    } else {
                        val epLink = app.get(decoded).documentLarge.selectFirst("article h3 a:contains(Episode 0$episode)")?.attr("href")
                        if (epLink != null) processBollyLink(epLink, subtitleCallback, callback)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun processBollyLink(url: String, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit) {
        val redirect = app.get(url, allowRedirects = false).headers["location"] ?: return
        if (redirect.contains("gdflix")) {
            GDFlix().getUrl(redirect, "BollyFlix", sub, cb)
        } else {
            loadSourceNameExtractor("BollyFlix", url, "", sub, cb)
        }
    }
}
