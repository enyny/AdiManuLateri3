package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min

object Lateri3PlayUtils {

    // --- Data Classes yang disederhanakan ---

    data class TmdbDate(
        val today: String,
        val nextWeek: String,
        val lastWeekStart: String,
        val monthStart: String
    )

    data class CinemaOsSecretKeyRequest(
        val tmdbId: String,
        val seasonId: String,
        val episodeId: String
    )
    
    // --- Utils Pendukung Extractor ---

    suspend fun loadSourceNameExtractor(
        source: String,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
        size: String = ""
    ) {
        val fixSize = if(size.isNotEmpty()) " $size" else ""
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        "$source[${link.source}$fixSize]",
                        "$source[${link.source}$fixSize]",
                        link.url,
                    ) {
                        this.quality = link.quality
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }
    
    // --- Helper Decoding (Dibutuhkan oleh Uqloadsxyz, PrimeSrc) ---
    
    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
    
    fun hasHost(url: String): Boolean {
        return try {
            val host = URL(url).host
            !host.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    fun String.getHost(): String {
        return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
    }

    // Mengganti fungsi getTMDBIdFromIMDB (Sederhana)
    // Dalam skema real, ini akan membutuhkan API call. Untuk tujuan ini, kita akan kembalikan null
    // dan berasumsi pemanggil (extractor) dapat mengatasinya.
    fun getTMDBIdFromIMDB(imdbId: String?): Int? {
        return if (imdbId.isNullOrBlank()) null else imdbId.removePrefix("tt").toIntOrNull()
    }
    
    // Uqloadsxyz Hash Placeholder (Sangat disederhanakan)
    fun getEpisodeLinkHash(tmdbId: Int?, season: Int?, episode: Int?): String {
        return if (season != null && episode != null) {
            "${tmdbId}_S${season}E${episode}"
        } else {
            tmdbId.toString()
        }
    }
    
    // --- Utils Generik ---

    fun getDate(): TmdbDate {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        val today = formatter.format(calendar.time)

        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val nextWeek = formatter.format(calendar.time)

        calendar.time = Date()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        val lastWeekStart = formatter.format(calendar.time)

        calendar.time = Date()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = formatter.format(calendar.time)

        return TmdbDate(today, nextWeek, lastWeekStart, monthStart)
    }

    // --- Subtitle Language Mapping ---

    val languageMap: Map<String, Set<String>> = mapOf(
        "English"     to setOf("en", "eng"),
        "Spanish"     to setOf("es", "spa"),
        "French"      to setOf("fr", "fra", "fre"),
        "German"      to setOf("de", "deu", "ger"),
        "Japanese"    to setOf("ja", "jpn"),
        "Korean"      to setOf("ko", "kor"),
        "Chinese"     to setOf("zh", "zho", "chi"),
        "Portuguese"  to setOf("pt", "por"),
        "Russian"     to setOf("ru", "rus"),
        "Turkish"     to setOf("tr", "tur"),
        // Tambahkan bahasa lain sesuai kebutuhan
    )

    fun getLanguage(code: String): String {
        val lower = code.lowercase()
        return languageMap.entries.firstOrNull { lower in it.value }?.key ?: "UnKnown"
    }
    
    // --- RiveStream Helpers ---
    
    fun extractKeyList(js: String): List<String> {
        val keyListRegex = Regex("""let\s+c\s*=\s*(\[[^]]*])""")
        val arrayString = keyListRegex.findAll(js).firstOrNull()?.groupValues?.get(1) ?: return emptyList()
        return Regex("\"([^\"]+)\"").findAll(arrayString).map { it.groupValues[1] }.toList()
    }

    // --- Multi Async Runner (Dibutuhkan oleh Lateri3Play.kt) ---

    /**
     * Run multiple suspend functions concurrently with a limit on simultaneous executions.
     */
    suspend fun runLimitedAsync(
        concurrency: Int = 5,
        vararg tasks: suspend () -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency)

        tasks.map { task ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        task()
                    } catch (e: Exception) {
                        Log.e("runLimitedAsync", "Task failed: ${e.message}")
                    }
                }
            }
        }.awaitAll()
    }
}
