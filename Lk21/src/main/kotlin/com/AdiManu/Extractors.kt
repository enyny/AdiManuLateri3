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
        
        // PERBAIKAN: Gunakan 'url' asli (iframe) sebagai referer agar M3U8 valid
        M3u8Helper.generateM3u8(this.name, file, url).forEach(callback)
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// Extractor tambahan untuk menangani mirror seperti f16px.com
class VidHideClone : ExtractorApi() {
    override val name = "VidHide Mirror"
    override val mainUrl = "https://f16px.com"
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        // Logika internal library akan mencoba memprosesnya sebagai VidHide
        com.lagradost.cloudstream3.extractors.VidHidePro6().getUrl(url, referer, subtitleCallback, callback)
    }
}
