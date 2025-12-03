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
        // HAPUS versionCode dan versionName dari sini karena ini adalah Library Module
        
        // API Key TMDb Khusus sesuai permintaan
        buildConfigField("String", "TMDB_API", "\"1cfadd9dbfc534abf6de40e1e7eaf4c7\"")
        
        // URL API untuk Subtitle dan Provider tertentu
        buildConfigField("String", "Whvx_API", "\"https://api.whvx.net\"")
        buildConfigField("String", "ZSHOW_API", "\"https://zshow.tv\"")
        buildConfigField("String", "MOVIE_API", "\"https://moviehub-api.vercel.app\"")
    }
}

cloudstream {
    // HAPUS baris 'name = ...' karena properti ini read-only.
    // Nama plugin akan otomatis diambil dari metadata kelas utama.
    
    language = "id"
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

    // Icon
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
