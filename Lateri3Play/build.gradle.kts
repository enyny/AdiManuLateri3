@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        // Mengatur namespace sesuai permintaan
        // Variabel ini diisi berdasarkan hasil dump dari file 1_dump.txt dan analisis sebelumnya
        
        // --- API KEYS UTAMA ---
        buildConfigField("String", "TMDB_API", "\"98ae14df2b8d8f8f8136499daf79f0e0\"") 
        
        // --- API URLS ---
        buildConfigField("String", "KissKh", "\"https://kisskh.ovh\"")
        buildConfigField("String", "KisskhSub", "\"https://kisskh.ovh/api/Sub/\"")
        buildConfigField("String", "FlixHQAPI", "\"https://flixhq.to\"") // Default fallback
        
        // --- SUPERSTREAM / MOVIEBOX KEYS (Ditemukan di Dump 1) ---
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"https://api.inmoviebox.com\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"https://api.inmoviebox.com\"") // Menggunakan endpoint utama yang ditemukan
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"https://api.inmoviebox.com\"")
        
        // Catatan: Key HMAC ini ditemukan di dump 1_dump.txt terkait com.community.mbox.in
        // Saya memasukkan placeholder yang valid berdasarkan struktur dump jika nilai hex spesifik tidak tercantum eksplisit di dump gradle
        // Namun, berdasarkan dump 1, kita bisa menggunakan mekanisme generate signature di Utils nanti.
        // Untuk menghindari error build, kita isi dengan string kosong atau key default, 
        // logika utama akan ada di Utils menggunakan algoritma yang ditemukan.
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"DefaultSecretKeyPlaceHolder\"") 
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"AltSecretKeyPlaceHolder\"")

        // --- ENCRYPTION/DECRYPTION KEYS ---
        // Key ini ditemukan di dump untuk layanan VidSrc/VidPlus dan lainnya
        buildConfigField("String", "Vidsrccc", "\"https://vidsrc.cc\"")
        buildConfigField("String", "RidomoviesAPI", "\"https://ridomovies.tv\"")
        
        // --- URL PROXY & HELPER ---
        // Diambil dari dump 1 (Google Scripts)
        buildConfigField("String", "KAISVA", "\"https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec\"")
        buildConfigField("String", "KAIMEG", "\"https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec\"")
        
        // --- LAINNYA ---
        buildConfigField("String", "Nuviostreams", "\"https://nuviostreams.com\"")
        buildConfigField("String", "VideasyDEC", "\"https://enc-dec.app/api/dec-videasy\"") // Dari dump 1
        buildConfigField("String", "YFXENC", "\"https://enc-dec.app/api/enc-kai\"") // Placeholder YFlix/Kai enc
        buildConfigField("String", "YFXDEC", "\"https://enc-dec.app/api/dec-kai\"") // Placeholder YFlix/Kai dec
    }
}

cloudstream {
    language = "en"
    description = "Lateri3Play - Movie & TV Provider"
    authors = listOf("AdiManuLateri3")
    status = 1 
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama"
    )
    // Icon custom atau default
    iconUrl = "https://i3.wp.com/yt3.googleusercontent.com/ytc/AIdro_nCBArSmvOc6o-k2hTYpLtQMPrKqGtAw_nC20rxm70akA=s900-c-k-c0x00ffffff-no-rj?ssl=1"

    requiresResources = true
    isCrossPlatform = false
}

dependencies {
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.browser:browser:1.9.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
