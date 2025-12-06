package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import java.net.URL

// ================== LATERI3PLAY EXTRACTORS (MULTI-HOST) ==================

open class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val realUrl = url.takeIf {
            try { URL(it); true } catch (e: Exception) { false }
        } ?: return

        val baseUrl = getBaseUrl(realUrl)
        
        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val rawHref = app.get(realUrl).documentLarge.select("#download").attr("href")
                if (rawHref.startsWith("http", ignoreCase = true)) rawHref 
                else baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
            }
        } catch (e: Exception) {
            return
        }

        if (href.isBlank()) return

        val document = app.get(href).documentLarge
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val quality = getQuality(header)

        val labelExtras = " $size"

        document.select("div.card-body h2 a.btn").forEach { element ->
            val link = element.attr("href")
            val text = element.text()

            when {
                text.contains("PixelDrain", true) || text.contains("Pixel", true) -> {
                    val finalURL = if (link.contains("download", true)) link
                    else "https://pixeldrain.com/api/file/${link.substringAfterLast("/")}?download"

                    callback.invoke(
                        newExtractorLink(
                            "Pixeldrain",
                            "Pixeldrain$labelExtras",
                            finalURL
                        ) { this.quality = quality }
                    )
                }
                text.contains("Instant Download", true) || text.contains("Fast Server", true) -> {
                     callback.invoke(
                        newExtractorLink(
                            "$referer [Instant]",
                            "$referer [Instant]$labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }
                text.contains("Gofile", true) -> {
                     Gofile().getUrl(link, referer, subtitleCallback, callback)
                }
                else -> {
                    loadExtractor(link, referer, subtitleCallback, callback)
                }
            }
        }
    }
}

open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url).documentLarge
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
        } catch (e: Exception) {
            url
        } ?: url

        val document = app.get(newUrl).documentLarge
        val size = document.select("ul > li.list-group-item:contains(Size)").text().substringAfter("Size : ")
        
        document.select("div.text-center a").forEach { anchor ->
            val text = anchor.text()
            val link = anchor.attr("href")

            when {
                text.contains("Instant DL", true) -> {
                    val finalLink = app.get(link, allowRedirects = false)
                        .headers["location"]?.substringAfter("url=").orEmpty()
                    
                    if (finalLink.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                "$referer [Instant]",
                                "$referer [Instant] $size",
                                finalLink
                            ) { this.quality = Qualities.Unknown.value }
                        )
                    }
                }
                text.contains("PixelDrain", true) -> {
                     callback.invoke(
                        newExtractorLink(
                            "Pixeldrain",
                            "Pixeldrain $size",
                            link
                        ) { this.quality = Qualities.Unknown.value }
                    )
                }
                text.contains("Gofile", true) -> {
                    try {
                        val doc = app.get(link).documentLarge
                        doc.select("a").forEach { 
                            if(it.attr("href").contains("gofile.io")) {
                                Gofile().getUrl(it.attr("href"), referer, subtitleCallback, callback)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

// ================== STANDARD EXTRACTORS ==================

class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val id = url.substringAfter("/u/").substringBefore("/")
        if (id.isNotEmpty()) {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = "https://pixeldrain.com/api/file/${id}?download"
                ) {
                    this.referer = "https://pixeldrain.com/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return
            val tokenJson = app.post("$mainApi/accounts").text
            val token = JSONObject(tokenJson).getJSONObject("data").getString("token")
            val js = app.get("$mainUrl/dist/js/global.js").text
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""").find(js)?.groupValues?.getOrNull(1) ?: return
            val contentJson = app.get(
                "$mainApi/contents/$id?wt=$wt",
                headers = mapOf("Authorization" to "Bearer $token")
            ).text

            val data = JSONObject(contentJson).getJSONObject("data").getJSONObject("children")
            val firstKey = data.keys().next()
            val fileObj = data.getJSONObject(firstKey)
            
            val link = fileObj.getString("link")
            val quality = getQuality(fileObj.optString("name", ""))

            callback.invoke(
                newExtractorLink(name, name, link) {
                    this.quality = quality
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        } catch (e: Exception) {
            Log.e("Gofile", "Error: ${e.message}")
        }
    }
}

open class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override var mainUrl = "https://ridoo.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = response.documentLarge.selectFirst("script:containsData(sources:)")?.data() ?: return
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1)
        
        if (m3u8 != null) {
            callback.invoke(
                newExtractorLink(this.name, this.name, url = m3u8, ExtractorLinkType.M3U8) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }
}

open class Modflix : ExtractorApi() {
    override val name = "Modflix"
    override val mainUrl = "https://video-seed.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val token = url.substringAfter("url=")
        val json = app.post(
            "https://video-seed.xyz/api",
            data = mapOf("keys" to token),
            referer = url,
            headers = mapOf("x-token" to "video-seed.xyz")
        ).text
        
        val link = JSONObject(json).optString("url").replace("\\/", "/")
        if(link.startsWith("http")) {
             callback.invoke(newExtractorLink(name, name, link) { this.quality = Qualities.P720.value })
        }
    }
}

class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: "")?.groupValues?.getOrNull(1)
        if (m3u8 != null) M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
    }
}

// Aliases
class Driveleech : ExtractorApi() { override val name = "Driveleech"; override val mainUrl = "https://driveleech.org"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {} }
class Driveseed : ExtractorApi() { override val name = "Driveseed"; override val mainUrl = "https://driveseed.org"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {} }
class Filelions : ExtractorApi() { override val name = "Filelions"; override val mainUrl = "https://filelions.to"; override val requiresReferer = false; override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {} }

// ================== ADICINEMAX21 NEW EXTRACTORS ==================

class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val hash = url.split("/").last().substringAfter("data=")

        val m3uLink = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$referer"),
            referer = referer,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>().videoSource

        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = m3uLink,
                ExtractorLinkType.M3U8
            )
        )

        document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData =
                    getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(subtitle.label ?: ""),
                            subtitle.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str
                .contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }
    
    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )
}
