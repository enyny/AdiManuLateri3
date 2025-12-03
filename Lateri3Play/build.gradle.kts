// File: build.gradle.kts

@file:Suppress("UnstableApiUsage")

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        // Kita menghapus dependensi local.properties yang rumit
        // API Key TMDB akan kita tanam langsung di file Lateri3Play.kt
    }
}

cloudstream {
    language = "en"
    description = "Lateri3Play Provider"
    authors = listOf("AdiManu")
    status = 3 // Status: Working/Beta
    
    // Tipe konten yang didukung
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime"
    )

    // Icon URL (bisa diganti sesuai keinginan)
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
