package com.AdiManuLateri3

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI
import java.net.URL

// Helper local untuk file ini
private fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.P1080.value
}

// --- HubCloud Extractor ---
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
            try { URL(it); true } catch (e: Exception) { Log.e("HubCloud", "Invalid URL: ${e.message}"); false }
        } ?: return

        val baseUrl = getBaseUrl(realUrl)

        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val rawHref = app.get(realUrl).document.select("#download").attr("href")
                if (rawHref.startsWith("http", ignoreCase = true)) {
                    rawHref
                } else {
                    baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
                }
            }
        } catch (e: Exception) {
            Log.e("HubCloud", "Failed to extract href: ${e.message}")
            ""
        }
        if (href.isBlank()) return

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val headerDetails = cleanTitle(header)

        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }
        val quality = getIndexQuality(header)

        document.select("div.card-body h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()

            when {
                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) -> {
                    val baseUrlLink = getBaseUrl(link)
                    val finalURL = if (link.contains("download", true)) link
                    else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"

                    callback(
                        newExtractorLink(
                            "Pixeldrain",
                            "Pixeldrain $labelExtras",
                            finalURL
                        ) { this.quality = quality }
                    )
                }
                text.contains("10Gbps", ignoreCase = true) -> {
                     try {
                        val redirectUrl = app.get(link, allowRedirects = false).headers["location"]
                        if (redirectUrl != null && "link=" in redirectUrl) {
                            val finalLink = redirectUrl.substringAfter("link=")
                            callback(
                                newExtractorLink(
                                    "10Gbps",
                                    "10Gbps $labelExtras",
                                    finalLink
                                ) { this.quality = quality }
                            )
                        }
                    } catch (e: Exception) {}
                }
                else -> {
                    // Gunakan loadExtractor standar Cloudstream untuk link generic lainnya
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }
    
    private fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (e: Exception) { "" }
    }
}

// --- PixelDrain Extractor ---
open class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty()) {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = url
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        } else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = "$mainUrl/api/file/${mId}?download"
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

// --- GDFlix Extractor (Ported Logic) ---
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
        // Handle Refresh Meta Tag
        val newUrl = try {
            app.get(url)
                .document
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
        } catch (e: Exception) {
            url
        } ?: url

        val document = app.get(newUrl).document
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text().substringAfter("Name : ")
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text().substringAfter("Size : ")
        
        val quality = getIndexQuality(fileName)
        val sourceName = referer ?: "GDFlix"

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.select("a").text()
            val href = anchor.attr("href")

            when {
                text.contains("Instant DL", ignoreCase = true) -> {
                    try {
                        val link = app.get(href, allowRedirects = false)
                            .headers["location"]?.substringAfter("url=").orEmpty()
                        
                        if (link.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink("$sourceName Instant", "$sourceName Instant [$fileSize]", link) {
                                    this.quality = quality
                                }
                            )
                        }
                    } catch (e: Exception) { }
                }
                text.contains("PixelDrain", ignoreCase = true) -> {
                     callback.invoke(
                        newExtractorLink("$sourceName PixelDrain", "$sourceName PixelDrain [$fileSize]", href) {
                            this.quality = quality
                        }
                    )
                }
                 text.contains("Gofile", ignoreCase = true) -> {
                     Gofile().getUrl(href, referer, subtitleCallback, callback)
                }
                 text.contains("CLOUD DOWNLOAD", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink("$sourceName Cloud", "$sourceName Cloud [$fileSize]", href) {
                            this.quality = quality
                        }
                    )
                }
            }
        }
    }
}

// --- Gofile Extractor ---
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
            
            // Get Account Token
            val responseText = app.post("$mainApi/accounts").text
            val json = JSONObject(responseText)
            val token = json.getJSONObject("data").getString("token")

            // Get WT Token
            val globalJs = app.get("$mainUrl/dist/js/global.js").text
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""")
                .find(globalJs)?.groupValues?.getOrNull(1) ?: return

            // Get Content
            val responseTextfile = app.get(
                "$mainApi/contents/$id?wt=$wt",
                headers = mapOf("Authorization" to "Bearer $token")
            ).text

            val fileDataJson = JSONObject(responseTextfile)
            val data = fileDataJson.getJSONObject("data")
            val children = data.getJSONObject("children")
            val firstFileId = children.keys().asSequence().first()
            val fileObj = children.getJSONObject(firstFileId)

            val link = fileObj.getString("link")
            val fileName = fileObj.optString("name", "Gofile Video")
            
            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "Gofile $fileName",
                    link
                ) {
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        } catch (e: Exception) {
            Log.e("Gofile", "Error: ${e.message}")
        }
    }
}
