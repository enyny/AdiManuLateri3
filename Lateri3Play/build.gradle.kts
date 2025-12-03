// File: build.gradle.kts

import com.lagradost.cloudstream3.gradle.CloudstreamExtension 
import com.android.build.gradle.BaseExtension

@Suppress("DSL_SCOPE_VIOLATION") // Menghindari peringatan IDE yang salah
plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
}

android {
    namespace = "com.AdiManuLateri3"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        targetSdk = 33
        
        // Konfigurasi API Key TMDB (Sesuai permintaan)
        buildConfigField("String", "TMDB_API", "\"1cfadd9dbfc534abf6de40e1e7eaf4c7\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    // Mengaktifkan fitur BuildConfig agar kita bisa memanggil TMDB_API di kode Kotlin
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

cloudstream {
    // Metadata Ekstensi
    name = "Lateri3Play"
    description = "Provider Streaming Pilihan (Top 10 + Subs)"
    authors = listOf("AdiManuLateri3")
    language = "id" // Bahasa utama Indonesia
    version = 1

    // Tipe konten yang didukung
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AsianDrama",
        "Cartoon"
    )

    // Icon (Opsional, menggunakan placeholder default atau link gambar)
    iconUrl = "https://i.imgur.com/3Z6S9Qx.png" 
    
    isCrossPlatform = true
    requiresResources = true
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.Blatzar:NiceHttp:0.4.11") // Library HTTP standar CloudStream
    implementation("org.jsoup:jsoup:1.17.2") // Parsing HTML
    
    // Dependensi CloudStream Core (Stub)
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
