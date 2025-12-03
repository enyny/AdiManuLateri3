package com.AdiManuLateri3

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// ==================== CONFIGURATION & DOMAINS ====================

data class DomainsParser(
    val moviesdrive: String,
    @JsonProperty("HDHUB4u")
    val hdhub4u: String,
    @JsonProperty("4khdhub")
    val n4khdhub: String,
    @JsonProperty("MultiMovies")
    val multiMovies: String,
    val bollyflix: String,
    @JsonProperty("UHDMovies")
    val uhdmovies: String,
    val moviesmod: String,
    val topMovies: String,
    val hdmovie2: String,
    val vegamovies: String,
    val rogmovies: String,
    val luxmovies: String,
    val xprime: String,
    val extramovies: String,
    val dramadrip: String,
    val toonstream: String,
)

// ==================== COMMON RESPONSES ====================

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class UHDBackupUrl(
    @JsonProperty("url") val url: String? = null,
)

// ==================== SUBTITLES ====================

data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)

data class WyZIESUB(
    val id: String,
    val url: String,
    val flagUrl: String,
    val format: String,
    val display: String,
    val language: String,
    val media: String,
    val isHearingImpaired: Boolean,
)

// ==================== PROVIDER SPECIFIC PARSERS ====================

// --- RidoMovies ---
data class RidoSearch(
    @JsonProperty("data") var data: RidoData? = null,
)

data class RidoData(
    @JsonProperty("url") var url: String? = null,
    @JsonProperty("items") var items: ArrayList<RidoItems>? = arrayListOf(),
)

data class RidoItems(
    @JsonProperty("slug") var slug: String? = null,
    @JsonProperty("contentable") var contentable: RidoContentable? = null,
)

data class RidoContentable(
    @JsonProperty("imdbId") var imdbId: String? = null,
    @JsonProperty("tmdbId") var tmdbId: Int? = null,
)

data class RidoResponses(
    @JsonProperty("data") var data: ArrayList<RidoDataUrl>? = arrayListOf(),
)

data class RidoDataUrl(
    @JsonProperty("url") var url: String? = null,
)

// --- KissKH ---
data class KisskhResults(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

data class KisskhDetail(
    @JsonProperty("episodes") val episodes: ArrayList<KisskhEpisodes>? = arrayListOf(),
)

data class KisskhEpisodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Int?,
)

data class KisskhSources(
    @JsonProperty("Video") val video: String?,
    @JsonProperty("ThirdParty") val thirdParty: String?,
)

data class KisskhSubtitle(
    @JsonProperty("src") val src: String?,
    @JsonProperty("label") val label: String?,
)

data class KisskhKey(
    val id: String,
    val version: String,
    val key: String,
)

// --- OXXFile / Drive ---
data class oxxfile(
    val id: String,
    val code: String,
    val fileName: String,
    val size: Long,
    val driveLinks: List<DriveLink>,
    val metadata: Metadata,
    val createdAt: String,
    val views: Long,
    val status: String,
    val gdtotLink: String?,
    val gdtotName: String?,
    val hubcloudLink: String,
    val filepressLink: String,
    val vikingLink: String?,
    val pixeldrainLink: String?,
    @SerializedName("credential_index")
    val credentialIndex: Long,
    val duration: String?,
    val userName: String,
)

data class DriveLink(
    val fileId: String,
    val webViewLink: String,
    val driveLabel: String,
    val credentialIndex: Int,
    val isLoginDrive: Boolean,
    val isDrive2: Boolean
)

data class Metadata(
    val mimeType: String,
    val fileExtension: String,
    val modifiedTime: String,
    val createdTime: String,
    val pixeldrainConversionFailed: Boolean,
    val pixeldrainConversionError: String,
    val vikingConversionFailed: Boolean,
    val vikingConversionFailedAt: String
)

// --- VidSrc ---
data class Vidsrcccservers(
    val data: List<VidsrcccDaum>,
    val success: Boolean,
)

data class VidsrcccDaum(
    val name: String,
    val hash: String,
)

data class Vidsrcccm3u8(
    val data: VidsrcccData,
    val success: Boolean,
)

data class VidsrcccData(
    val type: String,
    val source: String,
)

// --- SuperStream (External) ---
data class ExternalResponse(
    @JsonProperty("code") val code: Int? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("data") val data: ExternalData? = null,
)

data class ExternalData(
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("file_list") val fileList: List<FileList>? = null,
)

data class FileList(
    @JsonProperty("fid") val fid: Long? = null,
    @JsonProperty("file_name") val fileName: String? = null,
    @JsonProperty("oss_fid") val ossFid: Long? = null,
)

data class ExternalSourcesWrapper(
    @JsonProperty("sources") val sources: List<ExternalSources>? = null
)

data class ExternalSources(
    @JsonProperty("source") val source: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("size") val size: String? = null,
)

// --- General Helper ---
data class DriveBotLink(
    @JsonProperty("url") val url: String? = null,
)
