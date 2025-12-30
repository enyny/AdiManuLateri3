import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
    }
}

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
}

android {
    // PERBAIKAN: Menggunakan properti compileSdk (bukan fungsi compileSdkVersion)
    compileSdk = 33

    defaultConfig {
        // PERBAIKAN: Menggunakan properti minSdk dan targetSdk
        minSdk = 21
        targetSdk = 33
        
        // PERBAIKAN: Menghapus versionCode dan versionName karena ini adalah library, bukan aplikasi.
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val cloudstream by configurations
    implementation(kotlin("stdlib"))
    implementation("com.github.Blatzar:NiceHttp:0.3.2")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    
    // UI Libraries
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.leanback:leanback:1.0.0")

    cloudstream("com.lagradost:cloudstream3:pre-release")
}

cloudstream {
    // PERBAIKAN: Menghapus properti 'name', 'label', dan 'recommends' yang menyebabkan error.
    // Nama plugin akan diambil dari nama folder/project secara otomatis.
    
    description = "Streaming provider AboruFilm (Based on SuperStream)"
    authors = listOf("AdiManu")
    
    // Status: 1 = OK, 2 = Slow, 3 = Beta/Broken
    status = 1 
    
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AsianDrama"
    )
}
