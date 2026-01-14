package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

// Extractor Generik
class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
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
        // Membersihkan ID dari URL
        val id = url.substringAfter("id=").substringBefore("&")
        
        // Request API
        val response = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf(
                        "r" to (referer ?: ""),
                        "d" to mainUrl,
                ),
                referer = url,
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                )
        ).text

        try {
            val json = JSONObject(response)
            val file = json.optString("file")
            
            if (file.isNotBlank() && file != "null") {
                Log.d("Phisher-Success", "File Found: $file")
                
                val properReferer = url 
                
                val headers = mapOf(
                    "Origin" to mainUrl,
                    "Referer" to properReferer,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )

                // Coba generate M3U8 standar
                val playlist = M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = file,
                    referer = properReferer,
                    headers = headers
                )

                if (playlist.isNotEmpty()) {
                    playlist.forEach(callback)
                } else {
                    // Fallback: Force link masuk jika M3u8Helper gagal
                    Log.d("Phisher-Warn", "M3u8Helper failed, forcing link: $file")
                    
                    // PERBAIKAN DI SINI:
                    // referer dan quality diatur di dalam .apply {}, bukan di dalam kurung newExtractorLink()
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = file,
                            type = INFER_TYPE
                        ).apply {
                            this.headers = headers
                            this.referer = properReferer
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            } else {
                 Log.d("Phisher-Error", "File is empty in JSON")
            }
        } catch (e: Exception) {
            Log.e("Phisher-Error", "Json parse error: ${e.message}")
        }
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}
