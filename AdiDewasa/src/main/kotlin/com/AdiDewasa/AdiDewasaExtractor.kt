package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.SubtitleFile
import org.json.JSONObject

// Import Kekuatan AdiDrakor
import com.AdiDrakor.AdiDrakorExtractor

object AdiDewasaExtractor {

    // --- FUNGSI UTAMA (DIPANGGIL DARI ADIDEWASA.KT) ---
    suspend fun invokeAll(
        info: AdiLinkInfo,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = info.url

        // Jalankan Paralel (Original + Hybrid)
        AppUtils.runAllAsync(
            // TUGAS A: LOAD DARI DRAMAFULL (SUMBER UTAMA)
            {
                invokeOriginalDramafull(targetUrl, subtitleCallback, callback)
            },

            // TUGAS B: LOAD DARI ADIDRAKOR EXTRACTORS (SUMBER CADANGAN)
            {
                if (info.title.isNotEmpty()) {
                    val isMovie = info.season == null
                    
                    // 1. Cari ID TMDB & IMDB menggunakan AdiDewasaUtils
                    val (tmdbId, imdbId) = AdiDewasaUtils.getTmdbAndImdbId(info.title, info.year, isMovie)

                    if (tmdbId != null) {
                        // 2. Load Subtitle Wyzie
                        AdiDewasaUtils.invokeWyzieSubtitle(tmdbId, info.season, info.episode, subtitleCallback)

                        // 3. Panggil Pasukan AdiDrakor (Hybrid)
                        invokeHybridExtractors(info, tmdbId, imdbId, subtitleCallback, callback)
                    }
                }
            }
        )
    }

    // --- LOGIKA ASLI DRAMAFULL ---
    private suspend fun invokeOriginalDramafull(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val doc = app.get(url, headers = AdiDewasaUtils.headers).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return
            
            val signedUrl = Regex("""window\.signedUrl\s*=\s*["'](.+?)["']""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return
            
            val res = app.get(signedUrl, headers = AdiDewasaUtils.headers).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return
            
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull() ?: return
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(
                    newExtractorLink(
                        "AdiDewasa",
                        "AdiDewasa",
                        bestQualityUrl,
                        INFER_TYPE
                    ) {
                        this.referer = url // PENTING: Fix Error 3002
                        this.headers = AdiDewasaUtils.headers
                    }
                )
                
                // Subtitle Bawaan
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        subtitleCallback(SubtitleFile("English (Original)", "https://dramafull.cc$subUrl"))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if Dramafull source fails
        }
    }

    // --- LOGIKA HYBRID (PANGGIL ADIDRAKOR) ---
    private suspend fun invokeHybridExtractors(
        info: AdiLinkInfo,
        tmdbId: Int,
        imdbId: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // a. Adimoviebox
        AdiDrakorExtractor.invokeAdimoviebox(info.title, info.year, info.season, info.episode, subtitleCallback, callback)
        
        // b. Idlix
        AdiDrakorExtractor.invokeIdlix(info.title, info.year, info.season, info.episode, subtitleCallback, callback)
        
        // c. Vidlink & Vidfast
        AdiDrakorExtractor.invokeVidlink(tmdbId, info.season, info.episode, callback)
        AdiDrakorExtractor.invokeVidfast(tmdbId, info.season, info.episode, subtitleCallback, callback)
        
        // d. Superembed
        AdiDrakorExtractor.invokeSuperembed(tmdbId, info.season, info.episode, subtitleCallback, callback)

        // e. Vidsrc (Perlu IMDB)
        if (imdbId != null) {
            AdiDrakorExtractor.invokeVidsrc(imdbId, info.season, info.episode, subtitleCallback, callback)
            AdiDrakorExtractor.invokeVidsrccc(tmdbId, imdbId, info.season, info.episode, subtitleCallback, callback)
            AdiDrakorExtractor.invokeWatchsomuch(imdbId, info.season, info.episode, subtitleCallback)
        }

        // f. Lainnya
        AdiDrakorExtractor.invokeXprime(tmdbId, info.title, info.year, info.season, info.episode, subtitleCallback, callback)
        AdiDrakorExtractor.invokeMapple(tmdbId, info.season, info.episode, subtitleCallback, callback)
    }
}
