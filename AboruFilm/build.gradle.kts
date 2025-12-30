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
    compileSdkVersion(33)

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(33)
        versionCode = 1
        versionName = "1.0.0"
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
    // Informasi Plugin
    name = "AboruFilm"
    label = "AboruFilm"
    description = "Streaming provider AboruFilm (Based on SuperStream)"
    authors = listOf("AdiManu")
    recommends = listOf("com.lagradost.cloudstream3.animeproviders")
    
    // Status: 1 = OK, 2 = Slow, 3 = Beta/Broken
    status = 1 
    
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AsianDrama"
    )
}
