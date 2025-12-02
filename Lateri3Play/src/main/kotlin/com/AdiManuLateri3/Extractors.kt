package com.AdiManuLateri3

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class UqloadsXyz : ExtractorApi() {
    override val name = "Uqloadsxyz"
    override val mainUrl = "https://uqloads.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Normalisasi URL dari /download/ ke /e/ (embed)
        val embedUrl = url.replace("/download/", "/e/")
        
        var response = app.get(embedUrl, referer = referer)
        
        // Cek jika ada iframe (redirect)
        val iframe = response.documentLarge.selectFirst("iframe")
        if (iframe != null) {
            response = app.get(
                iframe.attr("src"), headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ), referer = response.url
            )
        }

        // Ekstrak script (Packed atau Normal)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // Regex untuk mencari link HLS (m3u8)
        val regex = Regex("""hls2":"(?<hls2>[^"]+)"|hls4":"(?<hls4>[^"]+)"""")
        
        val links = regex.findAll(script)
            .mapNotNull { matchResult ->
                val hls2 = matchResult.groups["hls2"]?.value
                val hls4 = matchResult.groups["hls4"]?.value
                when {
                    hls2 != null -> hls2
                    hls4 != null -> "https://uqloads.xyz$hls4"
                    else -> null
                }
            }.toList()

        // Generate link
        links.forEach { m3u8 ->
            generateM3u8(
                name,
                m3u8,
                mainUrl
            ).forEach(callback)
        }
    }
}
