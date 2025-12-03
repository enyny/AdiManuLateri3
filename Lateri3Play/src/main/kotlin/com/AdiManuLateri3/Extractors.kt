package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.AdiManuLateri3.Lateri3PlayUtils.loadSourceNameExtractor
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI
import java.net.URL

// ================= BASE & GENERIC EXTRACTORS =================

open class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override var mainUrl = "https://ridoo.net"
    override val requiresReferer = true
    open val defaulQuality = Qualities.P1080.value

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        val quality = "qualityLabels.*\"(\\d{3,4})[pP]\"".toRegex().find(script)?.groupValues?.get(1)
        
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = m3u8 ?: return,
                INFER_TYPE
            ) {
                this.referer = mainUrl
                this.quality = quality?.toIntOrNull() ?: defaulQuality
            }
        )
    }
}

class Multimovies : Ridoo() {
    override val name = "Multimovies"
    override var mainUrl = "https://multimovies.cloud"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://alions.pro"
    override val requiresReferer = false
}

open class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/e/(\\w+)".toRegex().find(url)?.groupValues?.get(1) ?: return
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        
        if (m3u8 != null) {
            callback.invoke(
                newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8)
            )
        }
    }
}

// ================= COMPLEX HOSTS (HubCloud, GDFlix, Driveseed) =================

class HubCloud : ExtractorApi() {
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
        } catch (e: Exception) { "" }

        if (href.isBlank()) return

        val document = app.get(href).documentLarge
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val quality = getIndexQuality(header)
        val labelExtras = if (size.isNotEmpty()) " [$size]" else ""

        document.select("div.card-body h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()

            when {
                text.contains("FSL Server", true) -> {
                    callback.invoke(newExtractorLink("$referer [FSL]", "$referer [FSL]$labelExtras", link) { this.quality = quality })
                }
                text.contains("BuzzServer", true) -> {
                    val buzzResp = app.get("$link/download", referer = link, allowRedirects = false)
                    val dlink = buzzResp.headers["hx-redirect"].orEmpty()
                    if (dlink.isNotBlank()) {
                        callback.invoke(newExtractorLink("$referer [Buzz]", "$referer [Buzz]$labelExtras", dlink) { this.quality = quality })
                    }
                }
                text.contains("pixeldra", true) || text.contains("pixel", true) -> {
                    val finalURL = if (link.contains("download", true)) link
                    else "${getBaseUrl(link)}/api/file/${link.substringAfterLast("/")}?download"
                    callback(newExtractorLink("Pixeldrain", "Pixeldrain$labelExtras", finalURL) { this.quality = quality })
                }
                text.contains("10Gbps", true) -> {
                    var currentLink = link
                    var redirectUrl: String?
                    while (true) {
                        val response = app.get(currentLink, allowRedirects = false)
                        redirectUrl = response.headers["location"]
                        if (redirectUrl == null || "link=" in redirectUrl) break
                        currentLink = redirectUrl
                    }
                    val finalLink = redirectUrl?.substringAfter("link=") ?: ""
                    if(finalLink.isNotEmpty())
                        callback.invoke(newExtractorLink("$referer 10Gbps", "$referer 10Gbps$labelExtras", finalLink) { this.quality = quality })
                }
                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P1080.value
    }
    
    private fun getBaseUrl(url: String): String {
        return try { URI(url).let { "${it.scheme}://${it.host}" } } catch (_: Exception) { "" }
    }
}

open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?, // Using 'referer' parameter as 'source' name
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url).documentLarge.selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")?.substringAfter("url=")
        } catch (e: Exception) { null } ?: url

        val document = app.get(newUrl).documentLarge
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text().substringAfter("Name : ")
        val quality = getIndexQuality(fileName)
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text().substringAfter("Size : ")
        val sourceName = referer ?: "GDFlix"

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.text()
            val link = anchor.attr("href")

            when {
                text.contains("DIRECT DL", true) -> {
                    callback.invoke(newExtractorLink("$sourceName [Direct]", "$sourceName [Direct] [$fileSize]", link) { this.quality = quality })
                }
                text.contains("Instant DL", true) -> {
                    try {
                        val instantLink = app.get(link, allowRedirects = false).headers["location"]?.substringAfter("url=").orEmpty()
                        if(instantLink.isNotEmpty())
                            callback.invoke(newExtractorLink("$sourceName [Instant]", "$sourceName [Instant] [$fileSize]", instantLink) { this.quality = quality })
                    } catch (_: Exception) {}
                }
                text.contains("PixelDrain", true) -> {
                    callback.invoke(newExtractorLink("$sourceName [Pixeldrain]", "$sourceName [Pixeldrain] [$fileSize]", link) { this.quality = quality })
                }
                text.contains("Gofile", true) -> {
                     loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }
    
    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Qualities.Unknown.value
    }
}

// ================= OTHER HOSTS =================

open class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        val finalUrl = if (mId.isNullOrEmpty()) url else "$mainUrl/api/file/${mId}?download"
        callback.invoke(newExtractorLink(this.name, this.name, finalUrl) {
            this.referer = url
            this.quality = Qualities.P1080.value
        })
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
            val token = JSONObject(app.post("$mainApi/accounts").text).getJSONObject("data").getString("token")
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""").find(app.get("$mainUrl/dist/js/global.js").text)?.groupValues?.getOrNull(1) ?: return

            val response = app.get("$mainApi/contents/$id?wt=$wt", headers = mapOf("Authorization" to "Bearer $token")).text
            val data = JSONObject(response).getJSONObject("data").getJSONObject("children")
            val firstFile = data.getJSONObject(data.keys().next())
            
            callback.invoke(newExtractorLink("Gofile", "Gofile", firstFile.getString("link")) {
                this.headers = mapOf("Cookie" to "accountToken=$token")
            })
        } catch (e: Exception) { Log.e("Gofile", e.message.toString()) }
    }
}

open class Modflix : ExtractorApi() {
    override val name = "Modflix"
    override val mainUrl = "https://video-seed.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val token = url.substringAfter("url=")
        val json = app.post(
            "$mainUrl/api",
            data = mapOf("keys" to token),
            referer = url,
            headers = mapOf("x-token" to "video-seed.xyz")
        ).text
        val link = JSONObject(json).getString("url")
        callback.invoke(newExtractorLink(name, name, link))
    }
}

open class Driveseed : ExtractorApi() {
    override val name = "Driveseed"
    override val mainUrl = "https://driveseed.org"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val doc = app.get(url).documentLarge
        val quality = Regex("(\\d{3,4})[pP]").find(doc.selectFirst("li.list-group-item")?.text().orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: Qualities.P720.value
        
        doc.select("div.text-center > a").forEach {
            if (it.text().contains("Direct Links", true)) {
                val cfUrl = "$url?type=1" // Simple Cloudflare bypass attempt
                try {
                    val cfDoc = app.get(cfUrl).documentLarge
                    val link = cfDoc.selectFirst("a.btn-success")?.attr("href")
                    if (!link.isNullOrBlank()) {
                        callback(newExtractorLink("$name CF", "$name CF", link) { this.quality = quality })
                    }
                } catch (_: Exception) {}
            }
        }
    }
}

class Driveleech : Driveseed() {
    override val name = "Driveleech"
    override val mainUrl = "https://driveleech.org"
}
