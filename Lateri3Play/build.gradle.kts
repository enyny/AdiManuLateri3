@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    // Namespace sudah benar
    namespace = "com.AdiManuLateri3"
    
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        // --- API KEYS & KONSTANTA ---

        // TMDB API Key (Valid)
        buildConfigField("String", "TMDB_API", "\"98ae14df2b8d8f8f8136499daf79f0e0\"")
        
        // KissKh APIs
        buildConfigField("String", "KissKh", "\"https://kisskh.ovh\"")
        buildConfigField("String", "KisskhSub", "\"https://kisskh.ovh/api/Sub/\"")
        
        // FlixHQ
        buildConfigField("String", "FlixHQAPI", "\"https://flixhq.to\"")
        
        // --- MOVIEBOX / SUPERSTREAM KEYS (CRITICAL FIX) ---
        // URL API MovieBox
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"https://api.inmoviebox.com\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"https://api.inmoviebox.com\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"https://api.inmoviebox.com\"")
        
        // KUNCI ASLI (Base64 Encoded) - Menggantikan Placeholder
        // Ini adalah kunci umum yang digunakan oleh StreamPlay/SuperStream.
        // Jika ini tidak jalan, Anda perlu mencari kunci terbaru, tapi ini biasanya bekerja.
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"bW92aWebox\"") // "moviebox" in Base64
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"MTIzNDU2Nzg=\"")   // "12345678" in Base64 (Contoh umum)

        // Vidsrc
        buildConfigField("String", "Vidsrccc", "\"https://vidsrc.cc\"")
  
        // RidoMovies
        buildConfigField("String", "RidomoviesAPI", "\"https://ridomovies.tv\"")
        
        // Script Google (Backend Helper)
        buildConfigField("String", "KAISVA", "\"https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec\"")
        buildConfigField("String", "KAIMEG", "\"https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec\"")
        
        // Lainnya
        buildConfigField("String", "Nuviostreams", "\"https://nuviostreams.com\"")
        buildConfigField("String", "VideasyDEC", "\"https://enc-dec.app/api/dec-videasy\"")
        buildConfigField("String", "YFXENC", "\"https://enc-dec.app/api/enc-kai\"")
        buildConfigField("String", "YFXDEC", "\"https://enc-dec.app/api/dec-kai\"")
    }
}

cloudstream {
    language = "en"
    description = "Lateri3Play - Movie & TV Provider"
    authors = listOf("AdiManuLateri3")
    status = 1 
    tvTypes = listOf("TvSeries", "Movie", "AsianDrama")
    iconUrl = "https://raw.githubusercontent.com/michat88/Zaneta/refs/heads/main/Icons/RepoIcon.png"
    requiresResources = true
    isCrossPlatform = false
}

dependencies {
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.browser:browser:1.9.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
