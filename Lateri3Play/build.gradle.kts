@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

val cloudstreamUser = "AdiManuLateri3" 
val cloudstreamRepo = "Lateri3Play"

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        // API Key TMDb Khusus sesuai permintaan
        buildConfigField("String", "TMDB_API", "\"1cfadd9dbfc534abf6de40e1e7eaf4c7\"")
        
        // URL API untuk Subtitle dan Provider tertentu yang tidak memerlukan secret key kompleks
        buildConfigField("String", "Whvx_API", "\"https://api.whvx.net\"")
        buildConfigField("String", "ZSHOW_API", "\"https://zshow.tv\"")
        buildConfigField("String", "MOVIE_API", "\"https://moviehub-api.vercel.app\"")
        
        // Versi plugin
        versionCode = 1
        versionName = "1.0.0"
    }
}

cloudstream {
    // Metadata Plugin
    name = "Lateri3Play"
    language = "id" // Mengutamakan Indonesia/Inggris
    description = "Ringan, Cepat, dan Stabil. 10 Provider Pilihan + Wyzie Subs."
    authors = listOf("AdiManuLateri3")
    
    // Kategori konten
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama",
        "Cartoon"
    )

    // Icon (Bisa diganti nanti)
    iconUrl = "https://i.imgur.com/Op9Rjs5.png" 

    requiresResources = true
}

dependencies {
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.browser:browser:1.8.0")
    
    // CloudStream Core Dependency
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
