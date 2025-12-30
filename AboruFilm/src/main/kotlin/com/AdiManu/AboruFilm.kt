package com.AdiManu

import android.util.Base64
import com.google.gson.Gson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

open class AboruFilm : MainAPI() {
    override var name = "AboruFilm"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val instantLinkLoading = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        const val HARDCODED_TOKEN = "59e139fd173d9045a2b5fc13b40dfd87"
    }

    private val iv = "wEiphTn!"
    private val key = "123d6cedf626dy54233aa1w6"
    private val firstAPI = "https://showboxssl.shegu.net/api/api_client/"
    val secondAPI = "https://showboxapissl.stsoso.com/api/api_client/"
    val thirdAPI = "https://www.febbox.com"
    
    // Variabel API yang dibutuhkan Extractor
    val openSubAPI = "https://opensubtitles-v3.strem.io"
    val watchSomuchAPI = "https://watchsomuch.tv"
    
    val appId = "com.tdo.showbox"
    private val appVersion = "11.7"
    private val appVersionCode = "131"

    private val globalHeaders = mapOf(
        "Platform" to "android",
        "Accept" to "charset=utf-8",
        "User-Agent" to "okhttp/3.12.1"
    )

    // region Encryption
    object CipherUtils {
        fun encrypt(str: String, key: String, iv: String): String? {
            return try {
                val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
                val bArr = ByteArray(24)
                val bytes = key.toByteArray()
                System.arraycopy(bytes, 0, bArr, 0, minOf(bytes.size, 24))
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(bArr, "DESede"), IvParameterSpec(iv.toByteArray()))
                Base64.encodeToString(cipher.doFinal(str.toByteArray()), Base64.NO_WRAP)
            } catch (e: Exception) { null }
        }
        fun md5(str: String): String? {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(str.toByteArray())
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun getVerify(str: String?, str2: String, str3: String) = md5(md5(str2) + str3 + (str ?: ""))
    }
    // endregion

    // region SSL Client
    private fun buildClient(): OkHttpClient {
        val certPem = """MIIEFTCCAv2gAwIBAgIUCrILmXOevO03gUhhbEhG/wZb2uAwDQYJKoZIhvcNAQEL
BQAwgagxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQH
Ew1TYW4gRnJhbmNpc2NvMRkwFwYDVQQKExBDbG91ZGZsYXJlLCBJbmMuMRswGQYD
VQQLExJ3d3cuY2xvdWRmbGFyZS5jb20xNDAyBgNVBAMTK01hbmFnZWQgQ0EgM2Q0
ZDQ4ZTQ2ZmI3MGM1NzgxZmI0N2VhNzk4MjMxZDMwHhcNMjQwNjA0MDkxMTAwWhcN
MzkwNjAxMDkxMTAwWjAiMQswCQYDVQQGEwJVUzETMBEGA1UEAxMKQ2xvdWRmbGFy
ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJhpMlr/+IatuBqpuZuA
6QvqdI2QiFb1UMVujb/xiaBC/vqJMlMenLSDysk8xd4fLeC+GC8AyWf1IMJIz6d9
rBjOhN4D+MxvgphufkdIVqs63SqKcrr/ZL0JaRpxxEg/pKqSjH55Ik71keB8tt0m
mQ76WK1swMydOAqn6DIKVAi7wF9acWyX/6Ly+cmxfueLDZvkLigXl3gMHbuoa5Y+
CadqKl2qlijhnvjpuEbAvyDyXWe838TUi0PYMHVuOu7PV4By2LINsm+gKv83od4k
RCSWTrLKlgfqneqnudMrqeWckNUHGVB+3Lruw1ebB/Rs4gJ59VhJYpbNmM2mYT0r
VQkCAwEAAaOBuzCBuDATBgNVHSUEDDAKBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAA
MB0GA1UdDgQWBBSF9Jkz4ZkbS5+LANO3YGWZRuX/PDAfBgNVHSMEGDAWgBTj01Q6
MJPAjpPqCEcv8rjxAUTO9jBTBgNVHR8ETDBKMEigRqBEhkJodHRwOi8vY3JsLmNs
b3VkZmxhcmUuY29tL2U1YTYzNzc5LTQ3NWQtNGI5OS04YzQxLTIwMjE5MmZhNjNj
ZC5jcmwwDQYJKoZIhvcNAQELBQADggEBALD+9MsfANm7fbzYH5/lXl07hwn2KSN8
PH7zxyo87ED62IL9U7YOnhb3rqLS1RXUzyHEmb9kzYgzKzzNrELdKH77vNk172Vk
iRQwGD0MZiYNERWhmmBtjV1oxllz74fL4+aZTYAespIbOekmFn9NZJ+XSdyF9RqS
fzDiz27GP5ZSHHI6xwdUP+a87N/RnfI4UwGxyXvPpHfoAZWjoXDqLKKwEL36/Sqi
nGcp970y0gnZ2zI2ehqivsF7BATMZqvU+LJKCH8NEE2bnbCJ6qlPHZWZFNKYWBOe
I1Crf0gNAWD/q3HKGMVZiyxlhU6SsQS4/08tDXXQjWYfl6i3oviexSk="""

        val keyPem = """MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCYaTJa//iGrbga
qbmbgOkL6nSNkIhW9VDFbo2/8YmgQv76iTJTHpy0g8rJPMXeHy3gvhgvAMln9SDC
SM+nfawYzoTeA/jMb4KYbn5HSFarOt0qinK6/2S9CWkaccRIP6Sqkox+eSJO9ZHg
fLbdJpkO+litbMDMnTgKp+gyClQIu8BfWnFsl/+i8vnJsX7niw2b5C4oF5d4DB27
qGuWPgmnaipdqpYo4Z746bhGwL8g8l1nvN/E1ItD2DDFbjruz1eActiyDbJvoCr/
N6HeJEQklk6yypYH6p3qp7nTK6nlnJDVBxlQfty67sNXmwf0bOICefVYSWKWzZjN
pmE9K1UJAgMBAAECggEAQFvnxjKiJWkVPbkfJjHU91GtnxwB3sqfrYdmN0ANUE4K
MwydYikinj2q87iEi6wZ6PYM60hHRG1oRHKPsZgphJ4s0D3YIagS+0Bpdbtv0cW9
IBovoZR4WzUum1qgOqwZYmgZCM0pNjOPwr6XT6Ldbkw8BxvN/HmFcUZ/ECZ5XugW
cKqKoy0HSlxwXT4PUAgLVfL4KvWy4A4yJJF24zgRKE4QYveOR4nUFvoRdxhuAyYW
xsajItj6sc6Jyr9FJzdw5Ra9EFwcWFM4uDdjHoaQrjwKId9fkCA+9eUCERWKTxCR
P8mU4p2cAJYO+ME9fZfs8H2uqGNj13XUzoT6JzM8UwKBgQDUFZWcfmlgCM2BjU9c
8qhYjD2egT3qxWJLYSUTUZfdOGgB6lxTqnOhsy93xYmVInz6r9XEZsLVoQj/wcZk
p7y+MxjiWNcBcUmviwHee42fe6BQZHaYlAFtlAKNSiHumfq6AtXpZvkQZJWTSRyW
lI4LBEL6fSuqpk88EH9FXJbChwKBgQC3+F/1Qi3EoeohhWD+jMO0r8IblBd7jYbp
2zs17KQsCEyc1qyIaE+a8Ud8zUqsECKWBuSFsQ2qrR3jZW6DZOw8hmp1foYC+Jjr
C/BHyWsyYxrCoxpvSJMXCY6ulyFHjIZboopRVi/jgfowteMW6WyxvOMqVAqZtxRW
HyFbsa+/7wKBgQCGHRwd+SZjr01dZmHQcjaYwB5bNHlWE/nDlyvd2pQBNaE3zN8T
nU8/6tLSl50YLNYBpN22NBFzDEFnkj8F+bh2QlOzFuDnrZ8eHfZRnaoCNyg6jj0c
4UNB6v3uIPnyK3cM16wzy4Umo6SenfYxFsH4H3rHcg4B/OdQIVKKJzHC0wKBgQCj
QxhlX0WeqtJMzUE2pVVIlHF+Z/4u93ozLwts34USTosu5JRYublrl5QJfWY3LFqF
KbjDrEykmt1bYDijAn1jeSYg/xeOq2+JqB6klms7XBfzgyuCdrWSTDkDV7uA84SI
7cYySHpXPJH7iG7vdlevpCE0/0ApCgBSLW49IYMGoQKBgAxVRqAhLdA0RO+nTAC/
whOL5RGy5M2oXKfqNkzEt2k5og7xXY7ZoYTye5Byb3+wLpEJXW+V8FlfXk/u5ZI7
oFuZne+lYcCPMNDXdku6wKdf9gSnOSHOGMu8TvHcud4uIDYmFH5qabJL5GDoQi7Q
12XvK21e6GNOEaRRlTHz0qUB"""

        val cert = CertificateFactory.getInstance("X.509").generateCertificate(Base64.decode(certPem, Base64.DEFAULT).inputStream()) as java.security.cert.X509Certificate
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(keyPem, Base64.DEFAULT)))
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setKeyEntry("client", privateKey, "".toCharArray(), arrayOf(cert)) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, "".toCharArray()) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
        val sslContext = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, tmf.trustManagers, SecureRandom()) }
        return OkHttpClient.Builder().sslSocketFactory(sslContext.socketFactory, tmf.trustManagers[0] as X509TrustManager).build()
    }
    // endregion

    fun queryApi(query: String, useAlt: Boolean = false): String {
        val encrypted = CipherUtils.encrypt(query, key, iv)!!
        val body = Base64.encodeToString("""{"app_key":"${CipherUtils.md5("moviebox")}","verify":"${CipherUtils.getVerify(encrypted, "moviebox", key)}","encrypt_data":"$encrypted"}""".toByteArray(), Base64.NO_WRAP)
        val form = FormBody.Builder().add("data", body).add("appid", "27").add("platform", "android").add("version", appVersionCode).add("medium", "Website").add("token", HARDCODED_TOKEN).build()
        val request = Request.Builder().url(if (useAlt) secondAPI else firstAPI).headers(globalHeaders.toHeaders()).post(form).build()
        return buildClient().newCall(request).execute().body.string()
    }

    inline fun <reified T : Any> queryApiParsed(query: String): T = try { Gson().fromJson(queryApi(query), T::class.java) } catch (e: Exception) { Gson().fromJson(queryApi(query, true), T::class.java) }
    
    fun getExpiryDate() = APIHolder.unixTime + 43200

    data class SearchData(val id: Int?, val mid: Int?, val box_type: Int?, val title: String?, val poster: String?, val quality_tag: String?, val imdb_rating: String?)
    data class SearchResponseData(val data: List<SearchData> = emptyList())
    data class DataJSON(val data: List<HomeSection> = emptyList())
    data class HomeSection(val name: String?, val list: List<SearchData> = emptyList())

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val apiQuery = """{"childmode":"0","app_version":"$appVersion","appid":"$appId","module":"Home_list_type_v2","channel":"Website","page":"$page","lang":"en","type":"all","pagelimit":"20","expired_date":"${getExpiryDate()}","platform":"android"}"""
        val sections = queryApiParsed<DataJSON>(apiQuery).data.mapNotNull { section ->
            val items = section.list.mapNotNull { i ->
                newMovieSearchResponse(i.title ?: "", LoadData(i.id ?: return@mapNotNull null, i.box_type).toJson(), if (i.box_type == 2) TvType.TvSeries else TvType.Movie) {
                    posterUrl = i.poster; this.score = Score.from10(i.imdb_rating)
                }
            }
            if (items.isEmpty()) null else HomePageList(section.name ?: "Trending", items)
        }
        return newHomePageResponse(sections, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val apiQuery = """{"childmode":"0","app_version":"$appVersion","module":"Search3","channel":"Website","page":"1","lang":"en","type":"all","keyword":"$query","pagelimit":"15","expired_date":"${getExpiryDate()}","platform":"android","appid":"$appId"}"""
        return queryApiParsed<SearchResponseData>(apiQuery).data.mapNotNull {
            newMovieSearchResponse(it.title ?: "", LoadData(it.id ?: it.mid ?: return@mapNotNull null, it.box_type).toJson(), if (it.box_type == 2) TvType.TvSeries else TvType.Movie) { posterUrl = it.poster }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val isMovie = loadData.box_type == 1
        val apiQuery = if (isMovie) """{"childmode":"0","app_version":"$appVersion","appid":"$appId","module":"Movie_detail","mid":"${loadData.id}","lang":"en","expired_date":"${getExpiryDate()}","platform":"android"}"""
                       else """{"childmode":"0","app_version":"$appVersion","appid":"$appId","module":"TV_detail_1","display_all":"1","tid":"${loadData.id}","lang":"en","expired_date":"${getExpiryDate()}","platform":"android"}"""
        
        return if (isMovie) {
            val d = queryApiParsed<MovieDataProp>(apiQuery).data!!
            newMovieLoadResponse(d.title ?: "", url, TvType.Movie, LinkData(d.id!!, 1, null, null, d.id, d.imdb_id)) {
                this.posterUrl = d.poster_org ?: d.poster
                this.plot = d.description
                this.year = d.year
                addImdbId(d.imdb_id)
                this.score = Score.from10(d.imdb_rating)
            }
        } else {
            val d = queryApiParsed<SeriesDataProp>(apiQuery).data!!
            val eps = mutableListOf<Episode>()
            d.season.forEach { s ->
                val sQuery = """{"childmode":"0","app_version":"$appVersion","appid":"$appId","module":"TV_episode","season":"$s","tid":"${loadData.id}","lang":"en","expired_date":"${getExpiryDate()}","platform":"android"}"""
                queryApiParsed<SeriesSeasonProp>(sQuery).data?.forEach { ep ->
                    eps.add(newEpisode(LinkData(ep.id ?: ep.tid!!, 2, ep.season, ep.episode, d.id, d.imdb_id).toJson()) { 
                        this.name = ep.title
                        this.season = ep.season
                        this.episode = ep.episode
                        this.description = ep.synopsis 
                    })
                }
            }
            newTvSeriesLoadResponse(d.title ?: "", url, TvType.TvSeries, eps) { 
                this.posterUrl = d.poster_org ?: d.poster
                this.plot = d.description
                addImdbId(d.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parsed = parseJson<LinkData>(data)
        runAllAsync(
            { AboruFilmExtractor.invokeInternalSource(parsed.id, parsed.type, parsed.season, parsed.episode, subtitleCallback, callback) },
            { AboruFilmExtractor.invokeExternalSource(parsed.mediaId, parsed.type, parsed.season, parsed.episode, callback) },
            { AboruFilmExtractor.invokeOpenSubs(parsed.imdbId, parsed.season, parsed.episode, subtitleCallback) }
        )
        return true
    }
}
