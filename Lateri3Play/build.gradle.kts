@file:Suppress("UnstableApiUsage")

android {
    // Namespace wajib untuk resource ID agar tidak crash
    namespace = "com.AdiManuLateri3"
    
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        // targetSdk, versionCode, dan versionName dihapus karena menyebabkan error di Library Module
    }

    buildFeatures {
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
    // 'name' dihapus karena read-only. Nama plugin diambil dari nama class utama.
    language = "id"
    description = "Lateri3Play Provider - Multi Sources"
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
    iconUrl = "https://raw.githubusercontent.com/michat88/Zaneta/refs/heads/main/Icons/adi.png"

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
