package com.Adicinemax21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// ==============================
// 1. JENIUSPLAY (DIPERBAIKI & ANTI-ERROR)
// ==============================

open class Jeniusplay2 : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Ambil halaman embed
        val res = app.get(url, referer = referer)
        val document = res.document
        
        // Perbaikan pengambilan Hash: lebih aman dari parameter tambahan (&)
        val hash = url.substringAfter("data=").substringBefore("&")

        var videoUrl: String? = null

        // METODE 1: Coba Request API (Cara Lama)
        try {
            val json = app.post(
                url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ResponseSource>()
            
            videoUrl = json?.videoSource?.takeIf { it.isNotBlank() } 
                      ?: json?.securedLink?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            // Abaikan error API, lanjut ke metode fallback
        }

        // METODE 2: JS Unpack (Fallback Kuat)
        // Jika API gagal, bongkar kode JavaScript yang di-obfuscate
        if (videoUrl.isNullOrEmpty()) {
            document.select("script").forEach { script ->
                val data = script.data()
                if (data.contains("eval(function(p,a,c,k,e,d)")) {
                    val unpacked = getAndUnpack(data)
                    
                    // Mencari pola: file:"https://..." atau src:"https://..."
                    videoUrl = Regex("""file:\s*["']([^"']+)["']""").find(unpacked)?.groupValues?.get(1)
                        ?: Regex("""src:\s*["']([^"']+)["']""").find(unpacked)?.groupValues?.get(1)
                    
                    if (!videoUrl.isNullOrEmpty()) return@forEach
                }
            }
        }

        // Jika link ditemukan, kirim ke CloudStream
        if (!videoUrl.isNullOrEmpty()) {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    videoUrl!!,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    // Header penting agar tidak error 403 Forbidden
                    this.headers = mapOf(
                        "Origin" to mainUrl,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
                    )
                }
            )
        }

        // Ekstrak Subtitle (Dari JS yang di-unpack)
        document.select("script").forEach { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = getAndUnpack(script.data())
                if (unpacked.contains("tracks")) {
                    val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                    tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                getLanguage(subtitle.label ?: "Unknown"),
                                subtitle.file
                            )
                        )
                    }
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str.contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean? = null,
        @JsonProperty("videoSource") val videoSource: String? = null,
        @JsonProperty("securedLink") val securedLink: String? = null,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )
}

// ==============================
// 2. YFLIX EXTRACTORS (TETAP ADA / TIDAK DIHAPUS)
// ==============================

class Fourspromax : MegaUp() {
    override var mainUrl = "https://4spromax.site"
    override val requiresReferer = true
}

class Rapidairmax : MegaUp() {
    override var mainUrl = "https://rapidairmax.site"
    override val requiresReferer = true
}

class Rapidshare : MegaUp() {
    override var mainUrl = "https://rapidshare.cc"
    override val requiresReferer = true
}

open class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.live"
    override val requiresReferer = true

    // KUNCI RAHASIA DARI YFLIX (Decoded from classes.dex)
    private val SECRET_API_URL = "https://enc-dec.app/api/dec-mega"

    companion object {
        private val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
            "Accept" to "text/html, *//*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Priority" to "u=0",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "referer" to "https://yflix.to/",
        )
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mediaUrl = url.replace("/e/", "/media/").replace("/e2/", "/media/")
        val displayName = referer ?: this.name

        // 1. Ambil HTML Mentah
        val encodedResult = app.get(mediaUrl, headers = HEADERS)
            .parsedSafe<YflixResponse>()
            ?.result

        if (encodedResult == null) return

        // 2. Siapkan Payload untuk API Rahasia
        val body = """
        {
        "text": "$encodedResult",
        "agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
        }
        """.trimIndent()
            .trim()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        // 3. Kirim ke API Dekripsi
        val m3u8Data = app.post(SECRET_API_URL, requestBody = body).text
        
        if (m3u8Data.isBlank()) return

        try {
            val root = JSONObject(m3u8Data)
            val result = root.optJSONObject("result") ?: return

            // 4. Ambil Video
            val sources = result.optJSONArray("sources") ?: JSONArray()
            if (sources.length() > 0) {
                val firstSourceObj = sources.optJSONObject(0)
                val m3u8File = firstSourceObj?.optString("file")?.takeIf { it.isNotBlank() }
                    ?: sources.optString(0).takeIf { it.isNotBlank() }
                
                if (m3u8File != null) {
                    M3u8Helper.generateM3u8(displayName, m3u8File, mainUrl).forEach(callback)
                }
            }

            // 5. Ambil Subtitle (Embedded JSON)
            val tracks = result.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until tracks.length()) {
                val trackObj = tracks.optJSONObject(i) ?: continue
                val label = trackObj.optString("label").trim().takeIf { it.isNotEmpty() }
                val file = trackObj.optString("file").takeIf { it.isNotBlank() }
                if (label != null && file != null) {
                    subtitleCallback(newSubtitleFile(label, file))
                }
            }
            
            // 6. Ambil Subtitle (URL Parameter - Fallback)
            try {
                if (url.contains("sub.list=")) {
                    val subtitleUrl = URLDecoder.decode(
                        url.substringAfter("sub.list="),
                        StandardCharsets.UTF_8.name()
                    )
                    val response = app.get(subtitleUrl).text
                    tryParseJson<List<Map<String, Any>>>(response)?.forEach { sub ->
                        val file = sub["file"]?.toString()
                        val label = sub["label"]?.toString()
                        if (!file.isNullOrBlank() && !label.isNullOrBlank()) {
                            subtitleCallback(newSubtitleFile(label, file))
                        }
                    }
                }
            } catch (e: Exception) {
               // Ignore errors
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class YflixResponse(
        @JsonProperty("status") val status: Int,
        @JsonProperty("result") val result: String
    )
}
