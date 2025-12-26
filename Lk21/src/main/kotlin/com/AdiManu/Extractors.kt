package com.AdiManu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

// Menambahkan support untuk F16px (Server yang muncul di log)
class F16px : Filesim() {
    override val mainUrl = "https://f16px.com"
    override val name = "F16px"
    override val requiresReferer = true
}

// Menambahkan support untuk Short.icu (Server yang muncul di log)
class ShortIcu : Filesim() {
    override val mainUrl = "https://short.icu"
    override val name = "ShortIcu"
    override val requiresReferer = true
}

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
        // Perbaikan: Menambahkan User-Agent agar tidak diblokir
        val response = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf(
                        "r" to "",
                        "d" to mainUrl,
                ),
                referer = url,
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
        ).text
        
        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            Log.d("LayarKaca", "Hownetwork File: $file")
            
            if (file.isNotBlank() && !file.contains("404")) {
                M3u8Helper.generateM3u8(
                    this.name,
                    file,
                    referer = "$mainUrl/" // Memastikan referer m3u8 benar
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("LayarKaca", "Error parsing Hownetwork: ${e.message}")
        }
    }
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}
