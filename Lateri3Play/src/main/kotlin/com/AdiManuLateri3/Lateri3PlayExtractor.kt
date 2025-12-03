package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.api.Log // Import Log ditambahkan
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.newSubtitleFile // Import newSubtitleFile ditambahkan
import com.AdiManuLateri3.Lateri3Play.Companion.UHDMoviesAPI
import com.AdiManuLateri3.Lateri3Play.Companion.MultiMoviesAPI
import com.AdiManuLateri3.Lateri3Play.Companion.NineTvAPI
import com.AdiManuLateri3.Lateri3Play.Companion.RidoMoviesAPI
import com.AdiManuLateri3.Lateri3Play.Companion.ZoeChipAPI
import com.AdiManuLateri3.Lateri3Play.Companion.NepuAPI
import com.AdiManuLateri3.Lateri3Play.Companion.PlayDesiAPI
import com.AdiManuLateri3.Lateri3Play.Companion.MoflixAPI
import com.AdiManuLateri3.Lateri3Play.Companion.VidsrcAPI
import com.AdiManuLateri3.Lateri3Play.Companion.WatchSomuchAPI
import com.AdiManuLateri3.Lateri3Play.Companion.SubtitlesAPI
import com.AdiManuLateri3.Lateri3Play.Companion.WyZIESUBAPI
import org.jsoup.Jsoup
import java.net.URI
import java.util.Locale

object Lateri3PlayExtractor {

    suspend fun invokeUhdmovies(
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchTitle = title?.replace(" ", "+") ?: return
        val url = "$UHDMoviesAPI/?s=$searchTitle"
        
        val doc = app.get(url).documentLarge
        val searchResults = doc.select("a.movie-card, article.post a") 

        for (result in searchResults) {
            val href = result.attr("href")
            val text = result.text()
            if (year != null && !text.contains(year.toString()) && season == null) continue

            val detailDoc = app.get(href).documentLarge
            val links = if (season == null) {
                detailDoc.select("a:contains(Download), a:contains(Watch)").map { it.attr("href") }
            } else {
                val episodeRegex = Regex("(?i)(Episode\\s*$episode|E$episode\\b)")
                detailDoc.select("p, h3, h4").filter { 
                    it.text().contains("Season $season", true) 
                }.flatMap { seasonBlock ->
                    seasonBlock.nextElementSibling()?.select("a")?.filter { 
                        it.text().contains(episodeRegex) 
                    }?.map { it.attr("href") } ?: emptyList()
                }
            }

            links.forEach { link ->
                if (link.contains("drive") || link.contains("gdflix")) {
                    GDFlix().getUrl(link, "UHDMovies", subtitleCallback, callback)
                } else {
                    loadExtractor(link, subtitleCallback, callback)
                }
            }
        }
    }

    suspend fun invokeMultimovies(
        title: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.lowercase()?.replace(" ", "-")?.replace(":", "") ?: return
        val url = if (season == null) "$MultiMoviesAPI/movies/$slug" else "$MultiMoviesAPI/episodes/$slug-$season-x-$episode"

        val response = app.get(url)
        if (response.code != 200) return
        val doc = response.documentLarge

        doc.select("ul#playeroptionsul li").forEach { li ->
            val id = li.attr("data-post")
            val nume = li.attr("data-nume")
            val type = li.attr("data-type")

            val json = app.post(
                "$MultiMoviesAPI/wp-admin/admin-ajax.php",
                data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type)
            ).text

            val embedUrl = tryParseJson<ResponseHash>(json)?.embed_url ?: return@forEach
            val cleanUrl = embedUrl.trim('"').replace("\\", "")
            loadExtractor(cleanUrl, subtitleCallback, callback)
        }
    }

    data class ResponseHash(@JsonProperty("embed_url") val embed_url: String)

    suspend fun invokeNinetv(
        id: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) "$NineTvAPI/movie/$id" else "$NineTvAPI/tv/$id-$season-$episode"
        val res = app.get(url, referer = "https://pressplay.top/")
        val iframe = res.documentLarge.selectFirst("iframe")?.attr("src") ?: return
        loadExtractor(iframe, subtitleCallback, callback)
    }

    suspend fun invokeRidomovies(
        tmdbId: Int?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val query = imdbId ?: return
        val search = app.get("$RidoMoviesAPI/core/api/search?q=$query").parsedSafe<RidoSearch>()
        val slug = search?.data?.items?.firstOrNull { it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId }?.slug ?: return

        val contentId = if (season == null) slug else {
            val epUrl = "$RidoMoviesAPI/tv/$slug/season-$season/episode-$episode"
            val epRes = app.get(epUrl).text
            epRes.substringAfter("postid\":\"").substringBefore("\"")
        }

        val apiUrl = "$RidoMoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$contentId/videos"
        val videos = app.get(apiUrl).parsedSafe<RidoResponse>()?.data ?: return

        videos.forEach { video ->
            val iframe = Jsoup.parse(video.url ?: "").select("iframe").attr("data-src")
            loadExtractor(iframe, subtitleCallback, callback)
        }
    }

    data class RidoSearch(val data: RidoData?)
    data class RidoData(val items: List<RidoItem>?)
    data class RidoItem(val slug: String?, val contentable: RidoIds?)
    data class RidoIds(val tmdbId: Int?, val imdbId: String?)
    data class RidoResponse(val data: List<RidoVideo>?)
    data class RidoVideo(val url: String?)

    suspend fun invokeZoechip(
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit, // Ditambahkan agar match saat dipanggil dari provider list
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.lowercase()?.replace(" ", "-") ?: return
        val url = if (season == null) "$ZoeChipAPI/film/$slug-$year" else "$ZoeChipAPI/episode/$slug-season-$season-episode-$episode"

        val res = app.get(url)
        val movieId = res.documentLarge.selectFirst("#show_player_ajax")?.attr("movie-id") ?: return

        val ajaxUrl = "$ZoeChipAPI/wp-admin/admin-ajax.php"
        val serverRes = app.post(
            ajaxUrl,
            data = mapOf("action" to "lazy_player", "movieID" to movieId),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).documentLarge

        val servers = serverRes.select("li[data-server]")
        servers.forEach { server ->
            val serverUrl = server.attr("data-server")
            if (serverUrl.contains("filemoon") || serverUrl.contains("vidcloud")) {
                loadExtractor(serverUrl, subtitleCallback, callback) // Sekarang parameter tidak null
            }
        }
    }

    suspend fun invokeNepu(
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val search = app.get("$NepuAPI/ajax/posts?q=$title", headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsedSafe<NepuSearch>()
        val matched = search?.data?.firstOrNull { it.name?.contains(title ?: "", true) == true && (year == null || it.url?.contains(year.toString()) == true) } ?: return

        val mediaUrl = if (season == null) matched.url ?: return else "${matched.url}/season/$season/episode/$episode"
        val doc = app.get(mediaUrl).documentLarge
        val embedId = doc.selectFirst("a[data-embed]")?.attr("data-embed") ?: return

        val embedRes = app.post("$NepuAPI/ajax/embed", data = mapOf("id" to embedId), headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text
        val m3u8 = Regex("file\":\"(.*?)\"").find(embedRes)?.groupValues?.get(1)?.replace("\\", "")
        if (m3u8 != null) M3u8Helper.generateM3u8("Nepu", m3u8, NepuAPI).forEach(callback)
    }

    data class NepuSearch(val data: List<NepuItem>?)
    data class NepuItem(val name: String?, val url: String?)

    suspend fun invokePlaydesi(
        title: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.lowercase()?.replace(" ", "-") ?: return
        val url = if (season == null) "$PlayDesiAPI/$slug" else "$PlayDesiAPI/$slug-season-$season-episode-$episode-watch-online"
        val doc = app.get(url).documentLarge
        doc.select("div.entry-content iframe").forEach { iframe ->
            loadExtractor(iframe.attr("src"), subtitleCallback, callback)
        }
    }

    suspend fun invokeMoflix(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val rawId = if (season == null) "tmdb|movie|$tmdbId" else "tmdb|series|$tmdbId"
        val b64Id = android.util.Base64.encodeToString(rawId.toByteArray(), android.util.Base64.NO_WRAP)

        val url = if (season == null) {
            "$MoflixAPI/api/v1/titles/$b64Id?loader=titlePage"
        } else {
            val meta = app.get("$MoflixAPI/api/v1/titles/$b64Id?loader=titlePage").parsedSafe<MoflixMeta>()
            val internalId = meta?.title?.id ?: return
            "$MoflixAPI/api/v1/titles/$internalId/seasons/$season/episodes/$episode?loader=episodePage"
        }

        val res = app.get(url).parsedSafe<MoflixResponse>()
        val videos = res?.episode?.videos ?: res?.title?.videos ?: return

        videos.forEach { video ->
            if (video.src != null) {
                callback.invoke(
                    newExtractorLink(
                        "Moflix [${video.name}]",
                        "Moflix [${video.name}]",
                        video.src,
                        INFER_TYPE
                    ) {
                        quality = Qualities.P1080.value
                    }
                )
            }
        }
    }

    data class MoflixMeta(val title: MoflixObj?)
    data class MoflixResponse(val title: MoflixObj?, val episode: MoflixObj?)
    data class MoflixObj(val id: Int?, val videos: List<MoflixVideo>?)
    data class MoflixVideo(val name: String?, val src: String?)

    suspend fun invokeVidsrc(
        id: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) "$VidsrcAPI/v2/embed/movie/$id" else "$VidsrcAPI/v2/embed/tv/$id/$season/$episode"
        val doc = app.get(url).documentLarge
        doc.select("a[data-hash]").forEach { source ->
            val hash = source.attr("data-hash")
            val json = app.get("$VidsrcAPI/api/source/$hash").text
            if (json.contains("m3u8")) {
                val m3u8 = Regex("source\":\"(.*?)\"").find(json)?.groupValues?.get(1)
                if (m3u8 != null) M3u8Helper.generateM3u8("Vidsrc", m3u8, VidsrcAPI).forEach(callback)
            }
        }
    }

    suspend fun invokeWatchsomuch(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val id = imdbId?.removePrefix("tt") ?: return
        val data = mapOf("index" to "0", "mid" to id, "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45")
        
        val res = app.post("$WatchSomuchAPI/Watch/ajMovieTorrents.aspx", data = data, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsedSafe<WSMResponse>()
        val torrents = res?.movie?.torrents ?: return
        
        val target = if (season == null) torrents.firstOrNull() else torrents.find { it.season == season && it.episode == episode }
        val tid = target?.id ?: return
        val epStr = if (season != null) "S${season.toString().padStart(2,'0')}E${episode.toString().padStart(2,'0')}" else ""
        
        val subUrl = "$WatchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$tid&part=$epStr"
        val subRes = app.get(subUrl).parsedSafe<WSMSubResponse>()
        
        subRes?.subtitles?.forEach { sub ->
            val lang = sub.label?.substringBefore("&nbsp") ?: "Unknown"
            val link = if (sub.url?.startsWith("http") == true) sub.url else "$WatchSomuchAPI${sub.url}"
            subtitleCallback(newSubtitleFile(lang, link))
        }
    }

    data class WSMResponse(val movie: WSMMovie?)
    data class WSMMovie(val torrents: List<WSMTorrent>?)
    data class WSMTorrent(val id: Int?, val season: Int?, val episode: Int?)
    data class WSMSubResponse(val subtitles: List<WSMSub>?)
    data class WSMSub(val url: String?, val label: String?)

    suspend fun invokeSubtitleAPI(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        val url = if (season == null) "$SubtitlesAPI/subtitles/movie/$imdbId.json" else "$SubtitlesAPI/subtitles/series/$imdbId:$season:$episode.json"
        try {
            val res = app.get(url).parsedSafe<SubAPIResponse>()
            res?.subtitles?.forEach { sub ->
                val lang = Locale(sub.lang ?: "en").displayLanguage
                subtitleCallback(newSubtitleFile(lang, sub.url ?: return@forEach))
            }
        } catch (e: Exception) { Log.e("SubAPI", e.message.toString()) }
    }

    suspend fun invokeWyZIESUBAPI(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        if (imdbId == null) return
        val url = buildString { append("$WyZIESUBAPI/search?id=$imdbId"); if (season != null) append("&season=$season&episode=$episode") }
        try {
            val res = app.get(url).parsedSafe<List<WyzieSub>>()
            res?.forEach { sub -> subtitleCallback(newSubtitleFile(sub.display ?: sub.language ?: "Unknown", sub.url ?: return@forEach)) }
        } catch (e: Exception) { Log.e("Wyzie", e.message.toString()) }
    }

    data class SubAPIResponse(val subtitles: List<SubAPIItem>?)
    data class SubAPIItem(val lang: String?, val url: String?)
    data class WyzieSub(val display: String?, val language: String?, val url: String?)
}
