package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import org.json.JSONObject 

object Lateri3PlayExtractor {
    
    private const val TAG = "Lateri3Play"
    private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
    
    // API Subtitle (Bawaan Lateri3Play)
    private const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
    private const val WyZIESUBAPI = "https://sub.wyzie.ru"

    // API Baru dari Adicinemax21 & Update Idlix
    const val idlixAPI = "https://tv10.idlixku.com" // UPDATED
    const val vidsrcccAPI = "https://vidsrc.cc"
    const val vidSrcAPI = "https://vidsrc.net"
    const val mappleAPI = "https://mapple.uk"
    const val vidlinkAPI = "https://vidlink.pro"
    const val vidfastAPI = "https://vidfast.pro"
    const val vixsrcAPI = "https://vixsrc.to"
    const val superembedAPI = "https://multiembed.mov"
    const val Player4uApi = "https://player4u.xyz"

    private var cachedDomains: DomainsParser? = null

    // Fallback domains
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
            fallbackDomains
        }
    }

    // =========================================================================
    // BAGIAN 1: EXTRACTOR LAMA (DARI LATERI3PLAY) - TETAP DIPERTAHANKAN
    // =========================================================================

    suspend fun invokeUhdmovies(title: String? = null, year: Int? = null, season: Int? = null, episode: Int? = null, callback: (ExtractorLink) -> Unit, subtitleCallback: (SubtitleFile) -> Unit) {
        val api = getDomains().uhdmovies
        val searchTitle = title?.replace("-", " ")?.replace(":", " ") ?: return
        val searchUrl = if (season != null) "$api/search/$searchTitle $year" else "$api/search/$searchTitle"
        try {
            val searchRes = app.get(searchUrl)
            val url = searchRes.documentLarge.select("article div.entry-image a").firstOrNull()?.attr("href") ?: return
            val doc = app.get(url).documentLarge
            val seasonPattern = season?.let { "(?i)(S0?$it|Season 0?$it)" }
            val episodePattern = episode?.let { "(?i)(Episode $it)" }
            val selector = if (season == null) "div.entry-content p:matches($year)" else "div.entry-content p:matches($seasonPattern)"
            val epSelector = if (season == null) "a:matches((?i)(Download))" else "a:matches($episodePattern)"
            doc.select(selector).mapNotNull { it.nextElementSibling()?.select(epSelector)?.attr("href") }.forEach { link ->
                if (link.isNotBlank()) {
                    val finalLink = if (link.contains("unblockedgames")) bypassHrefli(link) else link
                    if (!finalLink.isNullOrBlank()) loadSourceNameExtractor("UHDMovies", finalLink, "", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "UHD Error: ${e.message}") }
    }

    suspend fun invokeVegamovies(title: String?, year: Int?, season: Int?, episode: Int?, imdbId: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = getDomains().vegamovies
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")?.trim().orEmpty()
        val query = if (season == null) "$fixtitle $year" else "$fixtitle season $season $year"
        val url = "$api/?s=$query"
        try {
            val searchDoc = app.get(url, interceptor = CloudflareKiller()).documentLarge
            for (article in searchDoc.select("article h2")) {
                val href = article.selectFirst("a")?.attr("href") ?: continue
                val doc = app.get(href).documentLarge
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
                            if (!dlLink.isNullOrBlank()) loadSourceNameExtractor("VegaMovies", dlLink, "$api/", subtitleCallback, callback)
                        }
                    }
                } else {
                    val seasonBlock = doc.select("h4:matches((?i)Season $season), h3:matches((?i)Season $season)")
                    seasonBlock.forEach { block ->
                        val epLinks = block.nextElementSibling()?.select("a:matches((?i)(Episode $episode|Ep\\s*$episode))") ?: return@forEach
                        epLinks.forEach { epLink ->
                            val epUrl = epLink.attr("href")
                            val epDoc = app.get(epUrl).documentLarge
                            epDoc.select("a:matches((?i)(V-Cloud|G-Direct))").forEach { loadSourceNameExtractor("VegaMovies", it.attr("href"), "$api/", subtitleCallback, callback) }
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "VegaMovies Error: ${e.message}") }
    }

    suspend fun invokeMoviesmod(imdbId: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
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
                    h3.nextElementSibling()?.select("a.maxbutton-episode-links")?.forEach { link ->
                        val decoded = base64Decode(link.attr("href").substringAfter("="))
                        val epDoc = app.get(decoded).documentLarge
                        val targetEp = epDoc.select("span strong").firstOrNull { it.text().contains("Episode $episode", true) }?.parent()?.closest("a")?.attr("href")
                        if (targetEp != null) {
                            val finalUrl = if (targetEp.contains("unblockedgames")) bypassHrefli(targetEp) else targetEp
                            if (finalUrl != null) loadSourceNameExtractor("MoviesMod", finalUrl, "$api/", subtitleCallback, callback)
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "MoviesMod Error: ${e.message}") }
    }

    suspend fun invokeMultimovies(title: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = getDomains().multiMovies
        val slug = title?.createSlug() ?: return
        val url = if (season == null) "$api/movies/$slug" else "$api/episodes/$slug-$season" + "x$episode"
        try {
            val response = app.get(url, interceptor = CloudflareKiller())
            if (response.code != 200) return
            response.documentLarge.select("ul#playeroptionsul li").forEach { li ->
                val type = li.attr("data-type")
                val post = li.attr("data-post")
                val nume = li.attr("data-nume")
                val ajax = app.post("$api/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to post, "nume" to nume, "type" to type), referer = url).parsedSafe<ResponseHash>()
                val embedUrl = ajax?.embed_url?.replace("\\", "")?.replace("\"", "")
                if (embedUrl != null && !embedUrl.contains("youtube")) loadSourceNameExtractor("MultiMovies", embedUrl, "$api/", subtitleCallback, callback)
            }
        } catch (e: Exception) { Log.e(TAG, "MultiMovies Error: ${e.message}") }
    }

    suspend fun invokeRidomovies(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = "https://ridomovies.tv"
        try {
            val searchRes = app.get("$api/core/api/search?q=$imdbId").parsedSafe<RidoSearch>()
            val slug = searchRes?.data?.items?.find { it.contentable?.tmdbId == tmdbId }?.slug ?: return
            val contentId = if (season != null) app.get("$api/tv/$slug/season-$season/episode-$episode").text.substringAfterLast("postid\":\"").substringBefore("\"") else slug
            val typePath = if (season == null) "movies" else "episodes"
            val videos = app.get("$api/core/api/$typePath/$contentId/videos").parsedSafe<RidoResponses>()
            videos?.data?.forEach { video ->
                val iframe = Jsoup.parse(video.url ?: "").select("iframe").attr("data-src")
                if (iframe.startsWith("https://closeload.top")) {
                    val unpacked = getAndUnpack(app.get(iframe, referer = "$api/").text)
                    val hash = Regex("\\(\"([^\"]+)\"\\);").find(unpacked)?.groupValues?.get(1) ?: ""
                    val m3u8 = base64Decode(base64Decode(hash).reversed()).split("|").getOrNull(1)
                    if (m3u8 != null) callback.invoke(newExtractorLink("Ridomovies", "Ridomovies", m3u8, ExtractorLinkType.M3U8) { this.referer = "https://closeload.top/" })
                } else {
                    loadSourceNameExtractor("Ridomovies", iframe, "$api/", subtitleCallback, callback)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "RidoMovies Error: ${e.message}") }
    }

    suspend fun invokeMoviesdrive(title: String?, season: Int?, episode: Int?, year: Int?, imdbId: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = getDomains().moviesdrive
        val query = if (season != null) "$title $season" else "$title $year"
        try {
            val link = app.get("$api/?s=$query").documentLarge.select("figure a").firstOrNull()?.attr("href") ?: return
            val doc = app.get(link).documentLarge
            val imdbLink = doc.selectFirst("a[href*=\"imdb.com\"]")?.attr("href")
            if (imdbId != null && imdbLink != null && !imdbLink.contains(imdbId)) return
            if (season == null) {
                doc.select("h5 a").forEach { extractMdrive(it.attr("href")).forEach { url -> processDriveLink(url, "MoviesDrive", subtitleCallback, callback) } }
            } else {
                val sBlock = doc.select("h5:matches((?i)Season\\s*0?$season)").first()
                val epUrl = sBlock?.nextElementSibling()?.selectFirst("a")?.attr("href") ?: return
                val epDoc = app.get(epUrl).documentLarge
                val epBlock = epDoc.select("h5:matches((?i)Episode\\s+0?$episode)").first()
                var sibling = epBlock?.nextElementSibling()
                while (sibling != null && sibling.tagName() != "hr") {
                    if (sibling.tagName() == "h5") {
                        val href = sibling.selectFirst("a")?.attr("href")
                        if (href != null && !href.contains("Zip", true)) processDriveLink(href, "MoviesDrive", subtitleCallback, callback)
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
                detailDoc.select("a.maxbutton-download-links").forEach { loadSourceNameExtractor(sourceName, it.attr("href"), "$api/", sub, cb) }
            } else {
                val sBlock = detailDoc.select("h3:matches((?i)Season\\s*$season)").first()
                var sibling = sBlock?.nextElementSibling()
                while (sibling != null) {
                    if (sibling.text().contains("Episode $episode", true) || sibling.select("a").text().contains("Episode $episode", true)) {
                        sibling.select("a").forEach { link -> loadSourceNameExtractor(sourceName, link.attr("href"), "$api/", sub, cb) }
                    }
                    sibling = sibling.nextElementSibling()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun invokeHdmovie2(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
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
                val ajax = app.post("$api/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to post, "nume" to nume, "type" to "movie"), headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = url).parsedSafe<ResponseHash>()
                val iframe = Jsoup.parse(ajax?.embed_url ?: "").select("iframe").attr("src")
                if (iframe.isNotEmpty()) loadSourceNameExtractor("HDMovie2", iframe, api, subtitleCallback, callback)
            }
        } catch (_: Exception) {}
    }

    suspend fun invokeTopMovies(imdbId: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = getDomains().topMovies
        val query = if (season == null) "$imdbId $year" else "$imdbId Season $season"
        try {
            val link = app.get("$api/search/$query").documentLarge.select("#content_box article a").attr("href")
            if (link.isBlank()) return
            val doc = app.get(link).documentLarge
            if (season == null) {
                doc.select("a.maxbutton-fast-server-gdrive").forEach {
                    val url = if (it.attr("href").contains("unblockedgames")) bypassHrefli(it.attr("href")) else it.attr("href")
                    if (url != null) loadSourceNameExtractor("TopMovies", url, "$api/", subtitleCallback, callback)
                }
            } else {
                val epLink = doc.select("span strong").firstOrNull { it.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE)) }?.parent()?.closest("a")?.attr("href")
                if (epLink != null) {
                    val url = if (epLink.contains("unblockedgames")) bypassHrefli(epLink) else epLink
                    if (url != null) loadSourceNameExtractor("TopMovies", url, "$api/", subtitleCallback, callback)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun invokeBollyflix(id: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = getDomains().bollyflix
        val query = if (season != null) "$id $season" else id
        try {
            val link = app.get("$api/search/$query", interceptor = CloudflareKiller()).documentLarge.selectFirst("div > article > a")?.attr("href") ?: return
            val doc = app.get(link).documentLarge
            val hTag = if (season == null) "h5" else "h4"
            val sTag = if (season != null) "Season $season" else ""
            doc.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(720p|1080p))").forEach { entry ->
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
        if (redirect.contains("gdflix")) GDFlix().getUrl(redirect, "BollyFlix", sub, cb)
        else loadSourceNameExtractor("BollyFlix", url, "", sub, cb)
    }

    suspend fun invokeSubtitleAPI(id: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        val url = if (season == null) "$SubtitlesAPI/subtitles/movie/$id.json" else "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
        try {
            app.get(url).parsedSafe<SubtitlesAPI>()?.subtitles?.forEach { sub ->
                val lang = getLanguage(sub.lang)
                subtitleCallback(newSubtitleFile(lang, sub.url))
            }
        } catch (e: Exception) { Log.e(TAG, "SubtitleAPI Error: ${e.message}") }
    }

    suspend fun invokeWyZIESUBAPI(id: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        if (id.isNullOrBlank()) return
        val url = StringBuilder("$WyZIESUBAPI/search?id=$id")
        if (season != null && episode != null) url.append("&season=$season&episode=$episode")
        try {
            tryParseJson<List<WyZIESUB>>(app.get(url.toString()).text)?.forEach {
                subtitleCallback(newSubtitleFile(it.display, it.url))
            }
        } catch (e: Exception) { Log.e(TAG, "WyZIE Error: ${e.message}") }
    }

    // =========================================================================
    // BAGIAN 2: SOURCE BARU (DARI ADICINEMAX21) - DITAMBAHKAN SESUAI PERMINTAAN
    // =========================================================================

    // ================== ADIDEWASA SOURCE ==================
    @Suppress("UNCHECKED_CAST")
    suspend fun invokeAdiDewasa(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseUrl = "https://dramafull.cc"
        val cleanQuery = AdiDewasaHelper.normalizeQuery(title)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20")
        val searchUrl = "$baseUrl/api/live-search/$encodedQuery"

        try {
            val searchRes = app.get(searchUrl, headers = AdiDewasaHelper.headers).parsedSafe<AdiDewasaSearchResponse>()
            val matchedItem = searchRes?.data?.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                AdiDewasaHelper.isFuzzyMatch(title, itemTitle)
            } ?: searchRes?.data?.firstOrNull()

            if (matchedItem == null) return 
            val slug = matchedItem.slug ?: return
            var targetUrl = "$baseUrl/film/$slug"
            val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document

            if (season != null && episode != null) {
                val episodeHref = doc.select("div.episode-item a, .episode-list a").find { 
                    val text = it.text().trim()
                    val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    epNum == episode
                }?.attr("href")
                if (episodeHref == null) return
                targetUrl = fixUrl(episodeHref, baseUrl)
            } else {
                val selectors = listOf("a.btn-watch", "a.watch-now", ".watch-button a", "div.last-episode a", ".film-buttons a.btn-primary")
                var foundUrl: String? = null
                for (selector in selectors) {
                    val el = doc.selectFirst(selector)
                    if (el != null) {
                        val href = el.attr("href")
                        if (href.isNotEmpty() && !href.contains("javascript") && href != "#") {
                            foundUrl = fixUrl(href, baseUrl)
                            break
                        }
                    }
                }
                if (foundUrl != null) targetUrl = foundUrl
            }

            val docPage = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
            val allScripts = docPage.select("script").joinToString(" ") { it.data() }
            val signedUrl = Regex("""signedUrl\s*=\s*["']([^"']+)["']""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/") ?: return
            
            val jsonResponseText = app.get(signedUrl, referer = targetUrl, headers = AdiDewasaHelper.headers).text
            val jsonObject = tryParseJson<Map<String, Any>>(jsonResponseText) ?: return
            val videoSource = jsonObject["video_source"] as? Map<String, String> ?: return
            
            videoSource.forEach { (quality, url) ->
                 if (url.isNotEmpty()) callback.invoke(newExtractorLink("AdiDewasa", "AdiDewasa ($quality)", url, INFER_TYPE))
            }
             
             val bestQualityKey = videoSource.keys.maxByOrNull { it.toIntOrNull() ?: 0 } ?: return
             val subJson = jsonObject["sub"] as? Map<String, Any>
             val subs = subJson?.get(bestQualityKey) as? List<String>
             subs?.forEach { subPath ->
                 subtitleCallback.invoke(newSubtitleFile("English", fixUrl(subPath, baseUrl)))
             }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ================== KISSKH SOURCE ==================
    suspend fun invokeKisskh(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainUrl = "https://kisskh.ovh"
        val KISSKH_API = "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
        val KISSKH_SUB_API = "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

        try {
            val searchRes = app.get("$mainUrl/api/DramaList/Search?q=$title&type=0").text
            val searchList = tryParseJson<ArrayList<KisskhMedia>>(searchRes) ?: return
            val matched = searchList.find { it.title.equals(title, true) } ?: searchList.firstOrNull { it.title?.contains(title, true) == true } ?: return
            val dramaId = matched.id ?: return
            
            val detailRes = app.get("$mainUrl/api/DramaList/Drama/$dramaId?isq=false").parsedSafe<KisskhDetail>() ?: return
            val episodes = detailRes.episodes ?: return
            val targetEp = if (season == null) episodes.lastOrNull() else episodes.find { it.number?.toInt() == episode }
            val epsId = targetEp?.id ?: return

            val kkeyVideo = app.get("$KISSKH_API$epsId&version=2.8.10").parsedSafe<KisskhKey>()?.key ?: ""
            val videoUrl = "$mainUrl/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkeyVideo"
            val sources = app.get(videoUrl).parsedSafe<KisskhSources>()

            listOfNotNull(sources?.video, sources?.thirdParty).forEach { link ->
                if (link.contains(".m3u8")) {
                    M3u8Helper.generateM3u8("Kisskh", link, referer = "$mainUrl/", headers = mapOf("Origin" to mainUrl)).forEach(callback)
                } else if (link.contains(".mp4")) {
                    callback.invoke(newExtractorLink("Kisskh", "Kisskh", link, ExtractorLinkType.VIDEO) { this.referer = mainUrl })
                }
            }

            val kkeySub = app.get("$KISSKH_SUB_API$epsId&version=2.8.10").parsedSafe<KisskhKey>()?.key ?: ""
            val subJson = app.get("$mainUrl/api/Sub/$epsId?kkey=$kkeySub").text
            tryParseJson<List<KisskhSubtitle>>(subJson)?.forEach { sub ->
                subtitleCallback.invoke(newSubtitleFile(sub.label ?: "Unknown", sub.src ?: return@forEach))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ================== ADIMOVIEBOX SOURCE ==================
    suspend fun invokeAdimoviebox(title: String, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val searchUrl = "https://moviebox.ph/wefeed-h5-bff/web/subject/search"
        val streamApi = "https://fmoviesunblocked.net"
        val searchBody = mapOf("keyword" to title, "page" to 1, "perPage" to 10, "subjectType" to 0).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        try {
            val searchRes = app.post(searchUrl, requestBody = searchBody).text
            val items = tryParseJson<AdimovieboxSearch>(searchRes)?.data?.items ?: return
            val matchedMedia = items.find { item ->
                val itemYear = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
                (item.title.equals(title, true)) || (item.title?.contains(title, true) == true && itemYear == year)
            } ?: return

            val subjectId = matchedMedia.subjectId ?: return
            val se = if (season == null) 0 else season
            val ep = if (episode == null) 0 else episode
            
            val playUrl = "$streamApi/wefeed-h5-bff/web/subject/play?subjectId=$subjectId&se=$se&ep=$ep"
            val validReferer = "$streamApi/spa/videoPlayPage/movies/${matchedMedia.detailPath}?id=$subjectId&type=/movie/detail&lang=en"

            val playRes = app.get(playUrl, referer = validReferer).text
            val streams = tryParseJson<AdimovieboxStreams>(playRes)?.data?.streams ?: return

            streams.reversed().forEach { source ->
                 callback.invoke(newExtractorLink("Adimoviebox", "Adimoviebox", source.url ?: return@forEach, INFER_TYPE) {
                        this.referer = validReferer
                        this.quality = getQualityFromName(source.resolutions)
                    })
            }

            val id = streams.firstOrNull()?.id
            val format = streams.firstOrNull()?.format
            if (id != null) {
                val subUrl = "$streamApi/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$subjectId"
                app.get(subUrl, referer = validReferer).parsedSafe<AdimovieboxCaptions>()?.data?.captions?.forEach { sub ->
                    subtitleCallback.invoke(newSubtitleFile(sub.lanName ?: "Unknown", sub.url ?: return@forEach))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ================== IDLIX SOURCE (REFACTORED NEW LOGIC) ==================
    suspend fun invokeIdlix(title: String? = null, year: Int? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "$idlixAPI/movie/$fixTitle-$year" else "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        
        try {
            val document = app.get(url).document
            val scriptRegex = """window\.idlixNonce=['"]([a-f0-9]+)['"].*?window\.idlixTime=(\d+).*?""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val script = document.select("script:containsData(window.idlix)").toString()
            val match = scriptRegex.find(script)
            val idlixNonce = match?.groups?.get(1)?.value ?: ""
            val idlixTime = match?.groups?.get(2)?.value ?: ""

            document.select("ul#playeroptionsul > li").forEach { li ->
                val id = li.attr("data-post")
                val nume = li.attr("data-nume")
                val type = li.attr("data-type")

                val jsonStr = app.post(
                    url = "$idlixAPI/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type, "_n" to idlixNonce, "_p" to id, "_t" to idlixTime
                    ),
                    referer = url,
                    headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest")
                ).parsedSafe<ResponseHash>() ?: return@forEach

                val metrix = parseJson<AesData>(jsonStr.embed_url).m
                val password = createKey(jsonStr.key ?: return@forEach, metrix)
                val decrypted = AesHelper.cryptoAESHandler(jsonStr.embed_url, password.toByteArray(), false)?.fixBloat() ?: return@forEach
                
                if (!decrypted.contains("youtube")) {
                    loadExtractor(decrypted, idlixAPI, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Idlix Error: ${e.message}")
        }
    }

    // IDLIX HELPER FUNCTIONS
    private fun createKey(r: String, m: String): String {
        val rList = r.split("\\x").filter { it.isNotEmpty() }.toTypedArray()
        var n = ""
        var reversedM = m.split("").reversed().joinToString("")
        while (reversedM.length % 4 != 0) reversedM += "="
        val decodedBytes = try {
            base64Decode(reversedM)
        } catch (_: Exception) {
            return ""
        }
        val decodedM = String(decodedBytes.toCharArray())
        for (s in decodedM.split("|")) {
            try {
                val index = Integer.parseInt(s)
                if (index in rList.indices) {
                    n += "\\x" + rList[index]
                }
            } catch (_: Exception) { }
        }
        return n
    }

    private fun String.fixBloat(): String {
        return this.replace("\"", "").replace("\\", "")
    }

    private data class AesData(@JsonProperty("m") val m: String)

    // ================== VIDSRCCC SOURCE ==================
    suspend fun invokeVidsrccc(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrcccAPI/v2/embed/movie/$tmdbId" else "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val v = script.substringAfter("v = \"").substringBefore("\";")
        val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")
        val serverUrl = if (season == null) "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId" else "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&imdbId=$imdbId&season=$season&episode=$episode"

        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.forEach {
            val sources = app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data ?: return@forEach
            if (it.name == "VidPlay") {
                callback.invoke(newExtractorLink("VidPlay", "VidPlay", sources.source ?: return@forEach, ExtractorLinkType.M3U8) { this.referer = "$vidsrcccAPI/" })
                sources.subtitles?.forEach { sub -> subtitleCallback.invoke(newSubtitleFile(sub.label ?: return@forEach, sub.file ?: return@forEach)) }
            } else if (it.name == "UpCloud") {
                val scriptData = app.get(sources.source ?: return@forEach, referer = "$vidsrcccAPI/").document.selectFirst("script:containsData(source =)")?.data()
                val iframe = Regex("source\\s*=\\s*\"([^\"]+)").find(scriptData ?: return@forEach)?.groupValues?.get(1)
                val id = iframe?.substringAfterLast("/")?.substringBefore("?")
                if(id != null) {
                     app.get("${iframe.substringBeforeLast("/")}/getSources?id=$id", headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = iframe).parsedSafe<UpcloudResult>()?.sources?.forEach { source ->
                        callback.invoke(newExtractorLink("UpCloud", "UpCloud", source.file ?: return@forEach, ExtractorLinkType.M3U8) { this.referer = "$vidsrcccAPI/" })
                    }
                }
            }
        }
    }

    // ================== VIDSRC SOURCE ==================
    suspend fun invokeVidsrc(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = "https://cloudnestra.com"
        val url = if (season == null) "$vidSrcAPI/embed/movie?imdb=$imdbId" else "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
        app.get(url).document.select(".serversList .server").forEach { server ->
            if (server.text().equals("CloudStream Pro", ignoreCase = true)) {
                val hash = app.get("$api/rcp/${server.attr("data-hash")}").text.substringAfter("/prorcp/").substringBefore("'")
                val res = app.get("$api/prorcp/$hash").text
                val m3u8Link = Regex("https:.*\\.m3u8").find(res)?.value
                if (m3u8Link != null) callback.invoke(newExtractorLink("Vidsrc", "Vidsrc", m3u8Link, ExtractorLinkType.M3U8))
            }
        }
    }

    // ================== MAPPLE SOURCE ==================
    suspend fun invokeMapple(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mediaType = if (season == null) "movie" else "tv"
        val url = if (season == null) "$mappleAPI/watch/$mediaType/$tmdbId" else "$mappleAPI/watch/$mediaType/$season-$episode/$tmdbId"
        val data = if (season == null) """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]""" else """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"$season-$episode","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]"""
        val headers = mapOf("Next-Action" to "403f7ef15810cd565978d2ac5b7815bb0ff20258a5")
        val res = app.post(url, requestBody = data.toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull()), headers = headers).text
        val videoLink = tryParseJson<MappleSources>(res.substringAfter("1:").trim())?.data?.stream_url
        if (videoLink != null) callback.invoke(newExtractorLink("Mapple", "Mapple", videoLink, ExtractorLinkType.M3U8) { this.referer = "$mappleAPI/" })
    }

    // ================== VIDLINK SOURCE ==================
    suspend fun invokeVidlink(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        val videoLink = app.get(url, interceptor = WebViewResolver(Regex("""$vidlinkAPI/api/b/$type/A{32}"""), timeout = 15_000L)).parsedSafe<VidlinkSources>()?.stream?.playlist
        if (videoLink != null) callback.invoke(newExtractorLink("Vidlink", "Vidlink", videoLink, ExtractorLinkType.M3U8) { this.referer = "$vidlinkAPI/" })
    }

    // ================== VIDFAST SOURCE ==================
    suspend fun invokeVidfast(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val module = "hezushon/1000076901076321/0b0ce221/cfe60245-021f-5d4d-bacb-0d469f83378f/uva/jeditawev/b0535941d898ebdb81f575b2cfd123f5d18c6464/y/APA91zAOxU2psY2_BvBqEmmjG6QvCoLjgoaI-xuoLxBYghvzgKAu-HtHNeQmwxNbHNpoVnCuX10eEes1lnTcI2l_lQApUiwfx2pza36CZB34X7VY0OCyNXtlq-bGVCkLslfNksi1k3B667BJycQ67wxc1OnfCc5PDPrF0BA8aZRyMXZ3-2yxVGp"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidfastAPI/$type/$tmdbId" else "$vidfastAPI/$type/$tmdbId/$season/$episode"
        val res = app.get(url, interceptor = WebViewResolver(Regex("""$vidfastAPI/$module/JEwECseLZdY"""), timeout = 15_000L)).text
        tryParseJson<ArrayList<VidFastServers>>(res)?.filter { it.description?.contains("Original audio") == true }?.forEachIndexed { index, server ->
                val source = app.get("$vidfastAPI/$module/Sdoi/${server.data}", referer = "$vidfastAPI/").parsedSafe<VidFastSources>()
                val linkUrl = source?.url
                if (linkUrl != null) {
                    callback.invoke(newExtractorLink("Vidfast", "Vidfast [${server.name}]", linkUrl, INFER_TYPE))
                    if (index == 1) source.tracks?.forEach { subtitleCallback.invoke(newSubtitleFile(it.label ?: return@forEach, it.file ?: return@forEach)) }
                }
            }
    }

    // ================== VIXSRC SOURCE ==================
    suspend fun invokeVixsrc(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val proxy = "https://proxy.heistotron.uk"
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vixsrcAPI/$type/$tmdbId" else "$vixsrcAPI/$type/$tmdbId/$season/$episode"
        val res = app.get(url).document.selectFirst("script:containsData(window.masterPlaylist)")?.data() ?: return
        val video1 = Regex("""'token':\s*'(\w+)'[\S\s]+'expires':\s*'(\w+)'[\S\s]+url:\s*'(\S+)'""").find(res)?.let {
                    val (token, expires, path) = it.destructured
                    "$path?token=$token&expires=$expires&h=1&lang=en"
                } ?: return
        val video2 = "$proxy/p/${base64Encode("$proxy/api/proxy/m3u8?url=${URLEncoder.encode(video1, "utf-8").replace("+", "%20")}&source=sakura|ananananananananaBatman!".toByteArray())}"
        listOf(VixsrcSource("Vixsrc [Alpha]", video1, url), VixsrcSource("Vixsrc [Beta]", video2, "$mappleAPI/")).forEach {
            callback.invoke(newExtractorLink(it.name, it.name, it.url, ExtractorLinkType.M3U8) { this.referer = it.referer; this.headers = mapOf("Accept" to "*/*") })
        }
    }

    // ================== SUPEREMBED SOURCE ==================
    suspend fun invokeSuperembed(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = "https://streamingnow.mov"
        val path = if (season == null) "" else "&s=$season&e=$episode"
        val token = app.get("$superembedAPI/directstream.php?video_id=$tmdbId&tmdb=1$path").url.substringAfter("?play=")
        val (server, id) = app.post("$api/response.php", data = mapOf("token" to token), headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document.select("ul.sources-list li:contains(vipstream-S)").let { it.attr("data-server") to it.attr("data-id") }
        val playUrl = "$api/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
        var iframe = app.get(playUrl).document.selectFirst("iframe.source-frame")?.attr("src")
        if (iframe == null) {
             iframe = app.post(playUrl, requestBody = "captcha_id=TEduRVR6NmZ3Sk5Jc3JpZEJCSlhTM25GREs2RCswK0VQN2ZsclI5KzNKL2cyV3dIaFEwZzNRRHVwMzdqVmoxV0t2QlBrNjNTY04wY2NSaHlWYS9Jc09nb25wZTV2YmxDSXNRZVNuQUpuRW5nbkF2dURsQUdJWVpwOWxUZzU5Tnh0NXllQjdYUG83Y0ZVaG1XRGtPOTBudnZvN0RFK0wxdGZvYXpFKzVNM2U1a2lBMG40REJmQ042SA%3D%3D&captcha_answer%5B%5D=8yhbjraxqf3o&captcha_answer%5B%5D=10zxn5vi746w&captcha_answer%5B%5D=gxfpe17tdwub".toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull())).document.selectFirst("iframe.source-frame")?.attr("src")
        }
        val json = app.get(iframe ?: return).text.substringAfter("Playerjs(").substringBefore(");")
        val video = """file:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)
        if (video != null) callback.invoke(newExtractorLink("Superembed", "Superembed", video, INFER_TYPE))
        
        """subtitle:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)?.split(",")?.forEach {
            val (subLang, subUrl) = Regex("""\[(\w+)](http\S+)""").find(it)?.destructured ?: return@forEach
            subtitleCallback.invoke(newSubtitleFile(subLang.trim(), subUrl.trim()))
        }
    }

    // ================== PLAYER4U SOURCE ==================
    suspend fun invokePlayer4U(title: String?, season: Int?, episode: Int?, year: Int?, callback: (ExtractorLink) -> Unit) {
        if (title == null) return
        val queryWithEpisode = season?.let { "$title S${"%02d".format(it)}E${"%02d".format(episode)}" }
        val baseQuery = queryWithEpisode ?: title
        val encodedQuery = baseQuery.replace(" ", "+")
        
        // Parallel fetch for pages 0-4
        try {
             (0..4).map { page ->
                val url = "$Player4uApi/embed?key=$encodedQuery" + if (page > 0) "&page=$page" else ""
                val doc = try { app.get(url, timeout = 10).document } catch(e:Exception) { null }
                doc?.select(".playbtnx")?.forEach { element ->
                    val titleText = element.text()?.split(" | ")?.lastOrNull() ?: ""
                    var match = false
                    if (season == null && episode == null) {
                         if (year != null && (titleText.startsWith("$title $year", ignoreCase = true) || titleText.startsWith("$title ($year)", ignoreCase = true))) match = true
                    } else {
                         if (season != null && episode != null && titleText.startsWith("$title S${"%02d".format(season)}E${"%02d".format(episode)}", ignoreCase = true)) match = true
                    }
                    
                    if (match) {
                        val namePart = titleText
                        val displayName = "Player4U"
                        val qualityMatch = Regex("""(\d{3,4}p|4K|CAM|HQ|HD|SD|WEBRip|DVDRip|BluRay|HDRip|TVRip|HDTC|PREDVD)""", RegexOption.IGNORE_CASE).find(displayName)?.value?.uppercase() ?: "UNKNOWN"
                        val quality = getPlayer4UQuality(qualityMatch)
                        val subPath = Regex("""go\('(.*?)'\)""").find(element.attr("onclick"))?.groupValues?.get(1)
                        if (subPath != null) {
                             val iframeSrc = try { app.get("$Player4uApi$subPath", timeout = 10, referer = Player4uApi).document.selectFirst("iframe")?.attr("src") } catch (e:Exception) { null }
                             if (iframeSrc != null) getPlayer4uUrl(displayName, quality, "https://uqloads.xyz/e/$iframeSrc", Player4uApi, callback)
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}
