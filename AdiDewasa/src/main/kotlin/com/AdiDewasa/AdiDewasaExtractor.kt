package com.AdiDrakor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder

object AdiDrakorExtractor : AdiDrakor() {

    // ... [Code Adimoviebox, Gomovies, dll tetap sama, jangan dihapus] ...
    // Saya hanya menampilkan bagian yang DIUBAH (invokeAdiDewasa) agar tidak kepanjangan.
    // Pastikan funsi invokeAdimoviebox dll tetap ada di file Anda.

    // ================== ADIDEWASA / DRAMAFULL INTEGRATION ==================
    suspend fun invokeAdiDewasa(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseUrl = "https://dramafull.cc"
        
        // 1. Searching (Gunakan Judul Bersih)
        val cleanQuery = AdiDewasaHelper.normalizeQuery(title)
        val searchUrl = "$baseUrl/api/live-search/${URLEncoder.encode(cleanQuery, "UTF-8")}"

        try {
            val searchRes = app.get(searchUrl, headers = AdiDewasaHelper.headers).parsedSafe<AdiDewasaSearchResponse>()
            val items = searchRes?.data ?: return

            // 2. Matching (Logika Diperkuat)
            // Cari yang judulnya mirip DAN tahunnya sama (toleransi +/- 1 tahun)
            val matchedItem = items.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                val itemYear = item.year?.toIntOrNull() ?: 0
                
                val isTitleMatch = AdiDewasaHelper.isFuzzyMatch(title, itemTitle)
                val isYearMatch = if (year != null) {
                    (itemYear == year) || (itemYear == year - 1) || (itemYear == year + 1)
                } else true

                isTitleMatch && isYearMatch
            } ?: items.firstOrNull { 
                // Fallback: Jika tidak ada yang match tahun, ambil yang judulnya persis
                AdiDewasaHelper.isFuzzyMatch(title, it.title ?: it.name ?: "")
            }

            if (matchedItem == null) return
            
            val slug = matchedItem.slug ?: return
            var targetUrl = "$baseUrl/film/$slug"

            // 3. Handling TV Series vs Movie
            if (season != null && episode != null) {
                // Fetch halaman utama series untuk cari link episode
                val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
                
                // Cari link episode spesifik
                val episodeHref = doc.select("div.episode-item a, .episode-list a").find { 
                    val text = it.text().trim()
                    // Regex cari angka episode: "Episode 1" atau "1"
                    val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    epNum == episode
                }?.attr("href")

                if (episodeHref == null) return 
                targetUrl = fixUrl(episodeHref, baseUrl)
            } else {
                // Untuk Movie, pastikan kita di halaman player (biasanya sudah benar via slug)
                // Tapi kadang perlu klik "Watch Now"
                val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
                val watchButton = doc.selectFirst("a.btn-watch, a.watch-now, .watch-button a")
                if (watchButton != null) {
                    targetUrl = fixUrl(watchButton.attr("href"), baseUrl)
                }
            }

            // 4. Extraction Logic (Ditingkatkan)
            val docPage = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
            val allScripts = docPage.select("script").joinToString(" ") { it.data() }
            
            // Regex Original yang terbukti ampuh
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(allScripts)
                ?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: Regex("""signedUrl\s*=\s*['"]([^'"]+)['"]""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: return

            // Request Player JSON
            val jsonResponseText = app.get(
                signedUrl, 
                referer = targetUrl, 
                headers = AdiDewasaHelper.headers + mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text
            
            val jsonObject = tryParseJson<Map<String, Any>>(jsonResponseText) ?: return
            val videoSource = jsonObject["video_source"] as? Map<String, String> ?: return
            
            // Generate Links
            videoSource.forEach { (quality, link) ->
                 if (link.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "AdiDewasa",
                            "AdiDewasa ($quality)",
                            link,
                            ExtractorLinkType.M3U8
                        ) {
                            // Penting: Referer harus halaman film, bukan base url
                            this.referer = targetUrl 
                        }
                    )
                }
            }
             
             // Subtitles
             val bestQualityKey = videoSource.keys.maxByOrNull { it.toIntOrNull() ?: 0 }
             if (bestQualityKey != null) {
                 val subJson = jsonObject["sub"] as? Map<String, Any>
                 // Handle format JSON sub yang kadang beda (array vs object)
                 val subListRaw = subJson?.get(bestQualityKey)
                 
                 val subs = when (subListRaw) {
                     is List<*> -> subListRaw.filterIsInstance<String>()
                     else -> emptyList()
                 }

                 subs.forEach { subPath ->
                     val subUrl = if(subPath.startsWith("http")) subPath else "$baseUrl$subPath"
                     subtitleCallback.invoke(
                         newSubtitleFile("English", subUrl)
                     )
                 }
             }
             
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
