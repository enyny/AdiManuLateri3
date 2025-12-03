@file:Suppress("UnstableApiUsage")

android {
    // -----------------------------------------------------------------------
    // INI ADALAH KUNCI PERBAIKAN CRASH RESOURCE:
    // Namespace harus sama persis dengan packageName di MainSettingsFragment.kt
    // -----------------------------------------------------------------------
    namespace = "com.AdiManuLateri3"
    
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        // Diperlukan untuk mengakses BuildConfig.LIBRARY_PACKAGE_NAME jika needed
        // dan untuk ViewBinding di Fragment
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

cloudstream {
    // Metadata Ekstensi
    name = "Lateri3Play"
    language = "en"
    description = "Lateri3Play Provider - 10 Sources"
    authors = listOf("AdiManu")
    
    /**
     * Status int:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 3 

    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama"
    )

    // Icon URL
    iconUrl = "https://i3.wp.com/yt3.googleusercontent.com/ytc/AIdro_nCBArSmvOc6o-k2hTYpLtQMPrKqGtAw_nC20rxm70akA=s900-c-k-c0x00ffffff-no-rj?ssl=1"

    // Wajib true karena kita menggunakan layout XML custom
    requiresResources = true
}

dependencies {
    val cloudstream by configurations
    
    // Dependencies Standar
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Core CloudStream
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
