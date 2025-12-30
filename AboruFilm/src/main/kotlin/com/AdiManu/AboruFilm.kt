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
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        const val HARDCODED_TOKEN = "59e139fd173d9045a2b5fc13b40dfd87"
        fun getUnixTime() = System.currentTimeMillis() / 1000
    }

    private val iv = "wEiphTn!"
    private val key = "123d6cedf626dy54233aa1w6"
    val appId = "com.tdo.showbox"

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
CadqKl2qlijhnvjpuEbAvyDyXWe838TUi0PYMMVuOu7PV4By2LINsm+gKv83od4k
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
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, tmf.trustManagers, SecureRandom()) }
        return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, tmf.trustManagers[0] as X509TrustManager).build()
    }

    fun queryApi(q: String, alt: Boolean = false): String {
        val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
        val bArr = ByteArray(24); System.arraycopy(key.toByteArray(), 0, bArr, 0, minOf(key.length, 24))
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(bArr, "DESede"), IvParameterSpec(iv.toByteArray()))
        val enc = Base64.encodeToString(cipher.doFinal(q.toByteArray()), Base64.NO_WRAP)
        val md5 = MessageDigest.getInstance("MD5").let { it.update("moviebox".toByteArray()); it.digest().joinToString("") { "%02x".format(it) } }
        val verify = MessageDigest.getInstance("MD5").let { it.update((md5 + key + enc).toByteArray()); it.digest().joinToString("") { "%02x".format(it) } }
        val body = Base64.encodeToString("""{"app_key":"$md5","verify":"$verify","encrypt_data":"$enc"}""".toByteArray(), Base64.NO_WRAP)
        val form = FormBody.Builder().add("data", body).add("appid", "27").add("platform", "android").add("version", "131").add("medium", "Website").add("token", HARDCODED_TOKEN).build()
        val url = if (alt) "https://showboxapissl.stsoso.com/api/api_client/" else "https://showboxssl.shegu.net/api/api_client/"
        return buildClient().newCall(Request.Builder().url(url).post(form).build()).execute().body.string()
    }

    inline fun <reified T : Any> queryApiParsed(q: String): T = try { Gson().fromJson(queryApi(q), T::class.java) } catch (e: Exception) { Gson().fromJson(queryApi(q, true), T::class.java) }

    data class SearchData(val id: Int?, val mid: Int?, val box_type: Int?, val title: String?, val poster: String?, val imdb_rating: String?)
    data class SearchRes(val data: List<SearchData> = emptyList())
    data class HomeRes(val data: List<HomeSection> = emptyList())
    data class HomeSection(val name: String?, val list: List<SearchData> = emptyList())

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val q = """{"module":"Home_list_type_v2","page":"$page","lang":"en","type":"all","pagelimit":"20","expired_date":"${getUnixTime() + 43200}","platform":"android","appid":"$appId"}"""
        val data = queryApiParsed<HomeRes>(q)
        val sections = data.data.mapNotNull { s ->
            val items = s.list.mapNotNull { i ->
                newMovieSearchResponse(i.title ?: "", LoadData(i.id ?: return@mapNotNull null, i.box_type).toJson(), if (i.box_type == 2) TvType.TvSeries else TvType.Movie) { posterUrl = i.poster }
            }
            if (items.isEmpty()) null else HomePageList(s.name ?: "Trending", items)
        }
        return newHomePageResponse(sections, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = """{"module":"Search3","keyword":"$query","page":"1","pagelimit":"15","expired_date":"${getUnixTime() + 43200}","platform":"android","appid":"$appId"}"""
        return queryApiParsed<SearchRes>(q).data.mapNotNull { i ->
            newMovieSearchResponse(i.title ?: "", LoadData(i.id ?: i.mid ?: return@mapNotNull null, i.box_type).toJson(), if (i.box_type == 2) TvType.TvSeries else TvType.Movie) { posterUrl = i.poster }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val ld = parseJson<LoadData>(url)
        val q = if (ld.box_type == 1) """{"module":"Movie_detail","mid":"${ld.id}","expired_date":"${getUnixTime() + 43200}","platform":"android","appid":"$appId"}"""
                else """{"module":"TV_detail_1","tid":"${ld.id}","display_all":"1","expired_date":"${getUnixTime() + 43200}","platform":"android","appid":"$appId"}"""
        
        return if (ld.box_type == 1) {
            val d = queryApiParsed<MovieDataProp>(q).data!!
            newMovieLoadResponse(d.title ?: "", url, TvType.Movie, LinkData(d.id!!, 1, null, null, d.id, d.imdb_id)) { 
                this.posterUrl = d.poster
                this.plot = d.description // Perbaikan scope
                addImdbId(d.imdb_id)
            }
        } else {
            val d = queryApiParsed<SeriesDataProp>(q).data!!
            val eps = mutableListOf<Episode>()
            d.season.forEach { s ->
                val sq = """{"module":"TV_episode","season":"$s","tid":"${ld.id}","platform":"android","appid":"$appId"}"""
                queryApiParsed<SeriesSeasonProp>(sq).data?.forEach { ep ->
                    eps.add(newEpisode(LinkData(ep.id ?: ep.tid!!, 2, ep.season, ep.episode, d.id, d.imdb_id).toJson()) { name = ep.title; season = ep.season; episode = ep.episode })
                }
            }
            newTvSeriesLoadResponse(d.title ?: "", url, TvType.TvSeries, eps) { 
                this.posterUrl = d.poster
                this.plot = d.description // Perbaikan scope
                addImdbId(d.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val p = parseJson<LinkData>(data)
        runAllAsync(
            { AboruFilmExtractor.invokeInternalSource(p.id, p.type, p.season, p.episode, subCallback, callback) },
            { AboruFilmExtractor.invokeExternalSource(p.mediaId, p.type, p.season, p.episode, callback) },
            { AboruFilmExtractor.invokeOpenSubs(p.imdbId, p.season, p.episode, subCallback) },
            { AboruFilmExtractor.invokeWatchsomuch(p.imdbId, p.season, p.episode, subCallback) }
        )
        return true
    }
}
