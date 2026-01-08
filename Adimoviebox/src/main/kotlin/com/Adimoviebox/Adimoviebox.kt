package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.nicehttp.NiceResponse

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://fmoviesunblocked.net"
    // URL API Baru untuk Home
    private val homeApiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=moviebox.ph"

    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id" // Mengubah ke ID karena konten banyak yang Indonesia
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // Header khusus dari curl yang kamu berikan
    private val headers = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        // PENTING: Token ini mungkin kadaluarsa suatu saat. Jika error, token perlu diperbarui.
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjU0NzY2NTA2MTAxMzU1NTEzNDQsImF0cCI6MywiZXh0IjoiMTc2Nzg5MTEyMyIsImV4cCI6MTc3NTY2NzEyMywiaWF0IjoxNzY3ODkwODIzfQ.EFoAU--LYZMmyq83WvAwvVnGQiTJ27daeP3HWuA98VA",
        "content-type" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        "sec-ch-ua" to "\"Chromium\";v=\"107\", \"Not=A?Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "user-agent" to "Mozilla/5.0 (Linux; Android 13; CPH2235) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36",
        "x-request-lang" to "en"
    )

    // Kita hanya butuh satu entry point karena API mengembalikan semua kategori sekaligus
    override val mainPage = mainPageOf(
        "home" to "Home"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageData
    ): HomePageResponse {
        // Kita memuat semua data sekaligus di halaman 1, halaman selanjutnya kosong untuk menghindari duplikasi
        if (page > 1) return HomePageResponse(emptyList())

        val response = app.get(homeApiUrl, headers = headers).parsedSafe<HomeResponse>()
        val homePageList = ArrayList<HomePageList>()

        response?.data?.operatingList?.forEach { section ->
            // Mengabaikan section Filter dan Sport Live untuk saat ini agar rapi
            // Kamu bisa menghapus kondisi ini jika ingin menampilkan semuanya
            if (section.type == "FILTER" || section.type == "SPORT_LIVE") return@forEach

            val items = ArrayList<SearchResponse>()
            
            // Logika untuk mengambil item berdasarkan tipe section
            // Struktur JSON sedikit berbeda untuk setiap tipe
            val sourceList = when (section.type) {
                "BANNER" -> section.banner?.items
                "CUSTOM" -> section.customData?.items
                else -> section.subjects // Default untuk SUBJECTS_MOVIE, dll
            }

            sourceList?.forEach { item ->
                val title = item.title ?: item.subject?.title
                val id = item.subjectId ?: item.id
                // Mengambil URL gambar, prioritas dari 'image' lalu 'cover' lalu 'subject.cover'
                val coverUrl = item.image?.url ?: item.cover?.url ?: item.subject?.cover?.url
                
                if (!title.isNullOrEmpty() && !id.isNullOrEmpty() && id != "0") {
                    items.add(
                        newMovieSearchResponse(title, "$mainUrl/detail/$id") {
                            this.posterUrl = coverUrl
                            this.quality = SearchQuality.HD
                        }
                    )
                }
            }

            if (items.isNotEmpty()) {
                homePageList.add(
                    HomePageList(
                        section.title ?: "Featured",
                        items,
                        isHorizontalImages = section.type == "BANNER" // Banner ditampilkan horizontal
                    )
                )
            }
        }

        return HomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wefeed-h5-bff/web/subject/search"
        val json = """
            {"keyword":"$query","perPage":0,"subjectType":0}
        """.trimIndent()

        val response = app.post(
            url,
            headers = mapOf("Content-Type" to "application/json"),
            data = json
        ).parsedSafe<SearchData>()

        return response?.data?.items?.map { 
            it.toSearchResponse(this) 
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        // Mengambil ID dari URL
        val id = url.substringAfterLast("/")
        val detailUrl = "$mainUrl/wefeed-h5-bff/web/subject/$id"
        
        val response = app.get(detailUrl).parsedSafe<DetailResponse>()?.data 
            ?: throw ErrorLoadingException("Gagal memuat detail")

        val title = response.title ?: ""
        val poster = response.cover?.url
        val desc = response.description
        val rating = response.imdbRatingValue?.toRatingInt()
        val year = response.releaseDate?.take(4)?.toIntOrNull()
        
        // Menentukan tipe (Movie/TV)
        val isTv = response.subjectType == 2
        val type = if (isTv) TvType.TvSeries else TvType.Movie

        val episodes = ArrayList<Episode>()
        
        if (isTv) {
            // Logika sederhana untuk episode (bisa dikembangkan jika ada endpoint episode terpisah)
            // JSON detail biasanya berisi info episode jika strukturnya lengkap
            // Di sini kita pakai placeholder atau parse 'resourceList' jika ada
             val maxEp = response.episodesCount ?: 1
             (1..maxEp).forEach { epNum ->
                 episodes.add(
                     Episode(
                         data = "$id|$epNum", // Simpan ID dan EpNum untuk loadLinks
                         name = "Episode $epNum",
                         episode = epNum
                     )
                 )
             }
        } else {
            // Untuk Movie
            episodes.add(
                Episode(
                    data = "$id|1",
                    name = title,
                    episode = 1
                )
            )
        }

        return if (isTv) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.rating = rating
                this.tags = response.genre?.split(",")
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.rating = rating
                this.tags = response.genre?.split(",")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isBackup: Boolean,
        onSuccess: (SubtitleFile) -> Unit,
        onFailed: (SubtitleFile) -> Unit
    ): Boolean {
        // Implementasi loadLinks bergantung pada endpoint player/resource API baru
        // Karena endpoint player API berbeda dengan Home API, 
        // saya mempertahankan logika lama atau perlu endpoint spesifik untuk "get resource"
        // Jika kamu punya curl untuk memutar video, tolong berikan agar saya update bagian ini.
        
        // Placeholder logik (sesuaikan dengan apiUrl lama jika masih valid)
        return false 
    }

    // ================= DATA CLASSES (JSON MAPPING) =================

    data class HomeResponse(
        @JsonProperty("data") val data: HomeData? = null
    )

    data class HomeData(
        @JsonProperty("operatingList") val operatingList: List<OperatingList>? = null
    )

    data class OperatingList(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("banner") val banner: BannerSection? = null,
        @JsonProperty("subjects") val subjects: List<HomeItem>? = null,
        @JsonProperty("customData") val customData: CustomDataSection? = null
    )

    data class BannerSection(
        @JsonProperty("items") val items: List<HomeItem>? = null
    )
    
    data class CustomDataSection(
        @JsonProperty("items") val items: List<HomeItem>? = null
    )

    data class HomeItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("image") val image: ImageObj? = null,
        @JsonProperty("cover") val cover: ImageObj? = null, // Kadang ada di root item
        @JsonProperty("subject") val subject: SubjectDetail? = null
    )

    data class SubjectDetail(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: ImageObj? = null
    )

    data class ImageObj(
        @JsonProperty("url") val url: String? = null
    )

    // Data Class Search & Detail (Lama/Umum)
    data class SearchData(@JsonProperty("items") val items: List<SearchItem>? = null)
    
    data class SearchItem(
        @JsonProperty("subjectId") val subjectId: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: ImageObj?,
        @JsonProperty("subjectType") val subjectType: Int?
    ) {
        fun toSearchResponse(provider: MainAPI): SearchResponse {
            return provider.newMovieSearchResponse(title ?: "", "${provider.mainUrl}/detail/$subjectId") {
                this.posterUrl = cover?.url
            }
        }
    }

    data class DetailResponse(
        @JsonProperty("data") val data: DetailData? = null
    )
    
    data class DetailData(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("cover") val cover: ImageObj? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null,
        @JsonProperty("episodesCount") val episodesCount: Int? = null // Asumsi field
    )
}
