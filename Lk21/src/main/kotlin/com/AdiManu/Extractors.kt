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
        
        M3u8Helper.generateM3u8(this.name, file, file).forEach(callback)
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}
