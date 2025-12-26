package com.AdiManu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

// --- PERBAIKAN: F16px & ShortIcu menggunakan 'GenericM3u8Extractor' ---
// Kita buat kelas helper agar bisa dipakai berulang
open class GenericM3u8Extractor(override val name: String, override val mainUrl: String) : ExtractorApi() {
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // 1. Ambil Source Code Halaman
            val doc = app.get(url, referer = referer).text
            
            // 2. Cari link .m3u8 atau .mp4 menggunakan Regex
            // Mencari pola: "file": "..." atau src: "..."
            val regex = Regex("""(?i)(file|src|source)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            val match = regex.find(doc)
            
            if (match != null) {
                val foundLink = match.groupValues[2].replace("\\/", "/")
                Log.d("LayarKaca", "$name Found file: $foundLink")
                
                // 3. Generate Link
                M3u8Helper.generateM3u8(
                    this.name,
                    foundLink,
                    referer = url // Referer penting!
                ).forEach(callback)
            } else {
                Log.e("LayarKaca", "$name: No file found in source.")
            }
        } catch (e: Exception) {
            Log.e("LayarKaca", "$name Error: ${e.message}")
        }
    }
}

class F16px : GenericM3u8Extractor("F16px", "https://f16px.com")
class ShortIcu : GenericM3u8Extractor("ShortIcu", "https://short.icu")

// --- PERBAIKAN: Hownetwork ---
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
        // Request API
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
                // Perbaikan: Manual ExtractorLink jika M3u8Helper gagal
                // atau gunakan M3u8Helper dengan headers yang lengkap
                
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        file,
                        referer = "$mainUrl/",
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
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
