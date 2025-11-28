package com.AdiDewasa

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.newSubtitleFile
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.json.JSONObject
import java.net.URLEncoder

object AdiDewasaExtractor {

    private const val baseUrl = "https://dramafull.cc"

    suspend fun invokeAdiDewasa(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. PEMBERSIHAN JUDUL & PENCARIAN
        // Menggunakan helper yang sudah dibuat di Utils
        val cleanQuery = AdiDewasaHelper.normalizeQuery(title)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20")
        val searchUrl = "$baseUrl/api/live-search/$encodedQuery"

        try {
            // Request Search
            val searchRes = app.get(searchUrl, headers = AdiDewasaHelper.headers)
                .parsedSafe<ApiSearchResponse>()
            
            // 2. PENCOCOKAN JUDUL (Fuzzy Match)
            // Mencari yang paling mirip, jika tidak ada ambil yang pertama
            val matchedItem = searchRes?.data?.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                AdiDewasaHelper.isFuzzyMatch(title, itemTitle)
            } ?: searchRes?.data?.firstOrNull()

            if (matchedItem == null) return 

            val slug = matchedItem.slug ?: return
            var targetUrl = "$baseUrl/film/$slug"

            // 3. HANDLING EPISODE (Jika TV Series)
            if (season != null && episode != null) {
                // Load halaman utama series untuk cari episode
                val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
                
                // Cari link episode berdasarkan nomor
                // Regex mencari angka di dalam teks link (misal: "Episode 5" atau "Ep 05")
                val episodeHref = doc.select("div.episode-item a, .episode-list a, .episodes a").find { 
                    val text = it.text().trim()
                    val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    epNum == episode
                }?.attr("href")

                if (episodeHref == null) return // Episode tidak ditemukan
                
                // Fix URL jika relatif
                targetUrl = if (episodeHref.startsWith("http")) episodeHref else "$baseUrl$episodeHref"
            } 
            // HANDLING MOVIE (Cek tombol Watch Now jika ada redirect)
            else {
                val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
                val watchBtn = doc.selectFirst("a.btn-watch, a.watch-now, .watch-button a")
                val href = watchBtn?.attr("href")
                if (!href.isNullOrBlank() && !href.contains("javascript")) {
                    targetUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                }
            }

            // 4. EKSTRAKSI VIDEO (Logic Inti)
            val pageRes = app.get(targetUrl, headers = AdiDewasaHelper.headers)
            val pageBody = pageRes.text
            
            // Regex yang diperkuat untuk menangkap signedUrl dalam berbagai format kutip
            val signedUrlRegex = Regex("""signedUrl\s*=\s*["']([^"']+)["']""")
            val match = signedUrlRegex.find(pageBody)
            
            val rawSignedUrl = match?.groupValues?.get(1) ?: return
            // Bersihkan backslashes yang mungkin ada (contoh: https:\/\/...)
            val signedUrl = rawSignedUrl.replace("\\/", "/")
            
            // 5. GET JSON SOURCE
            // Penting: Referer harus di-set ke halaman video agar server memberikan data
            val jsonRes = app.get(signedUrl, headers = AdiDewasaHelper.headers, referer = targetUrl).text
            val jsonObject = tryParseJson<JSONObject>(jsonRes) ?: return
            
            // Ambil Video Source
            val videoSource = jsonObject.optJSONObject("video_source") ?: return
            
            // Cari kualitas terbaik untuk referensi subtitle nanti
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull()

            // 6. KIRIM LINK KE PLAYER
            videoSource.keys().forEach { quality ->
                val link = videoSource.optString(quality)
                if (link.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "AdiDewasa",
                            "AdiDewasa ($quality)",
                            link,
                            INFER_TYPE
                        )
                    )
                }
            }

            // 7. AMBIL SUBTITLE INTERNAL
            if (bestQualityKey != null) {
                val subJson = jsonObject.optJSONObject("sub")
                val subArray = subJson?.optJSONArray(bestQualityKey)
                
                if (subArray != null) {
                    for (i in 0 until subArray.length()) {
                        val subPath = subArray.getString(i)
                        val subUrl = if (subPath.startsWith("http")) subPath else "$baseUrl$subPath"
                        
                        subtitleCallback.invoke(
                            newSubtitleFile("English (Internal)", subUrl)
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
