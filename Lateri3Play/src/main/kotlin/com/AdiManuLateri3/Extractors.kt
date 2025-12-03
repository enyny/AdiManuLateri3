package com.AdiManuLateri3

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.amap
import org.json.JSONObject

// ================= GDFlix / UHDMovies Extractor =================
open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?, // Dalam konteks ini referer sering kita pakai sebagai Source Name (misal: UHDMovies)
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sourceName = referer ?: name
        
        // Handle Redirect GDFlix
        val resolvedUrl = try {
            app.get(url).documentLarge.selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")?.substringAfter("url=") ?: url
        } catch (e: Exception) { url }

        val doc = app.get(resolvedUrl).documentLarge
        val fileSize = doc.select("ul > li:contains(Size)").text().substringAfter(":").trim()
        val title = doc.select("div.card-header").text().trim()

        // Cari link download/stream
        doc.select("div.text-center a").amap { anchor ->
            val link = anchor.attr("href")
            val text = anchor.text().lowercase()

            if (link.contains("pixeldrain") || text.contains("pixel")) {
                PixelDrain().getUrl(link, sourceName, subtitleCallback, callback)
            } 
            else if (link.contains("gofile") || text.contains("gofile")) {
                Gofile().getUrl(link, sourceName, subtitleCallback, callback)
            }
            else if (text.contains("instant") || text.contains("cloud")) {
                // Biasanya direct link atau streamable
                val finalLink = app.get(link, allowRedirects = false).headers["location"] ?: link
                callback.invoke(
                    newExtractorLink(
                        "$sourceName [Instant]",
                        "$sourceName [Instant] $fileSize",
                        finalLink,
                        INFER_TYPE
                    ) {
                        this.quality = getQualityFromName(title)
                    }
                )
            }
        }
    }
}

// ================= HubCloud Extractor =================
class HubCloud : ExtractorApi() {
    override val name = "HubCloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sourceName = referer ?: name
        val doc = app.get(url).documentLarge
        val title = doc.select("div.card-header").text()
        
        doc.select("a.btn").amap { btn ->
            val href = btn.attr("href")
            val text = btn.text().lowercase()

            if (text.contains("pixel")) {
                PixelDrain().getUrl(href, sourceName, subtitleCallback, callback)
            } else if (text.contains("watch") || text.contains("download")) {
                loadExtractor(href, subtitleCallback, callback)
            }
        }
    }
}

// ================= Gofile Extractor (API Based) =================
class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Ambil Content ID
            val id = if (url.contains("gofile.io/d/")) {
                url.substringAfter("/d/")
            } else {
                return 
            }

            // Dapatkan Token Tamu (Guest Token)
            val accountObj = app.post("https://api.gofile.io/accounts").parsed<JSONObject>()
            val token = accountObj.getJSONObject("data").getString("token")

            // Ambil Info File
            val contentUrl = "https://api.gofile.io/contents/$id?wt=4fd6sg89d7s6"
            val contentRes = app.get(contentUrl, headers = mapOf("Authorization" to "Bearer $token")).text
            val contentJson = JSONObject(contentRes).getJSONObject("data").getJSONObject("children")

            // Parse setiap file dalam folder
            contentJson.keys().forEach { key ->
                val file = contentJson.getJSONObject(key)
                if (file.getString("type") == "file") {
                    val link = file.getString("link")
                    val fileName = file.getString("name")
                    
                    callback.invoke(
                        newExtractorLink(
                            "Gofile",
                            "Gofile $fileName",
                            link,
                            INFER_TYPE
                        ) {
                            this.headers = mapOf("Cookie" to "accountToken=$token")
                            this.quality = getQualityFromName(fileName)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Fail silently
        }
    }
}

// ================= PixelDrain Extractor =================
class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("/u/").substringAfter("/l/")
        if (id.isNotBlank()) {
            val directLink = "https://pixeldrain.com/api/file/$id"
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    directLink,
                    INFER_TYPE
                ) {
                    this.referer = "https://pixeldrain.com/"
                }
            )
        }
    }
}

// ================= Simple Wrapper Extractors =================
// Kelas kosong ini diperlukan agar Lateri3PlayPlugin.kt bisa me-register-nya
// meskipun logikanya sebagian besar ditangani oleh extractor generik.

class Multimovies : ExtractorApi() {
    override val name = "Multimovies"
    override val mainUrl = "https://multimovies.sbs"
    override val requiresReferer = true
    // Logika utama ada di Lateri3PlayExtractor, ini hanya untuk direct calls jika ada
}

class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override val mainUrl = "https://ridoo.net"
    override val requiresReferer = true
}
