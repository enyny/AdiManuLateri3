package com.AdiManuLateri3

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
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// ==============================
// JENIUSPLAY EXTRACTOR (FIXED FROM ADICINEMAX21)
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
        val document = app.get(url, referer = "$mainUrl/").document
        val hash = url.split("/").last().substringAfter("data=")

        val m3uLink = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$referer"),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>().videoSource

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                m3uLink,
                ExtractorLinkType.M3U8
            ) {
                this.referer = url
            }
        )

        document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData =
                    getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
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

// ==============================
// YFLIX EXTRACTORS (NEW)
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

        val encodedResult = app.get(mediaUrl, headers = HEADERS)
            .parsedSafe<YflixResponse>()
            ?.result

        if (encodedResult == null) return

        val body = """
        {
        "text": "$encodedResult",
        "agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
        }
        """.trimIndent()
            .trim()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val m3u8Data = app.post(SECRET_API_URL, requestBody = body).text
        
        if (m3u8Data.isBlank()) return

        try {
            val root = JSONObject(m3u8Data)
            val result = root.optJSONObject("result") ?: return

            val sources = result.optJSONArray("sources") ?: JSONArray()
            if (sources.length() > 0) {
                val firstSourceObj = sources.optJSONObject(0)
                val m3u8File = firstSourceObj?.optString("file")?.takeIf { it.isNotBlank() }
                    ?: sources.optString(0).takeIf { it.isNotBlank() }
                
                if (m3u8File != null) {
                    M3u8Helper.generateM3u8(displayName, m3u8File, mainUrl).forEach(callback)
                }
            }

            val tracks = result.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until tracks.length()) {
                val trackObj = tracks.optJSONObject(i) ?: continue
                val label = trackObj.optString("label").trim().takeIf { it.isNotEmpty() }
                val file = trackObj.optString("file").takeIf { it.isNotBlank() }
                if (label != null && file != null) {
                    subtitleCallback(newSubtitleFile(label, file))
                }
            }
            
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

// ==============================
// RESTORED OLD EXTRACTORS
// ==============================

class HubCloud : ExtractorApi() {
    override val name = "HubCloud"
    override val mainUrl = "https://hubcloud.club"
    override val requiresReferer = false
    // Implementasi standar atau placeholder jika source aslinya hilang
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://gdflix.top"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Modflix : ExtractorApi() {
    override val name = "Modflix"
    override val mainUrl = "https://modflix.xyz"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override val mainUrl = "https://ridoo.net"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Driveleech : ExtractorApi() {
    override val name = "Driveleech"
    override val mainUrl = "https://driveleech.org"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Driveseed : ExtractorApi() {
    override val name = "Driveseed"
    override val mainUrl = "https://driveseed.org"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Filelions : ExtractorApi() {
    override val name = "Filelions"
    override val mainUrl = "https://filelions.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}
