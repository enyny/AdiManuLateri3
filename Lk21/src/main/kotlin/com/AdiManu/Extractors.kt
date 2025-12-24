package com.AdiManu

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=")
        val response = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf("r" to "", "d" to mainUrl),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).text
        val json = JSONObject(response)
        val file = json.optString("file")
        
        // PERBAIKAN: Masukkan URL iframe asli sebagai referer ke helper agar M3U8 tidak dianggap invalid
        M3u8Helper.generateM3u8(this.name, file, url).forEach(callback)
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class VidHideClone : ExtractorApi() {
    override val name = "VidHide Mirror"
    override val mainUrl = "https://f16px.com"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        com.lagradost.cloudstream3.extractors.VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
    }
}

// Tambahan Hydrax karena muncul di Logcat kamu
class HydraxMirror : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://short.icu"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        // Hydrax biasanya diproses otomatis oleh library jika diarahkan dengan benar
        callback.invoke(ExtractorLink(this.name, url, referer ?: "", true))
    }
}
