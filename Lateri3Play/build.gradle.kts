@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 486

android {
    // FIX: Baris ini wajib ada agar BuildConfig digenerate di paket yang benar
    namespace = "com.AdiManuLateri3"

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        if (project.rootProject.file("local.properties").exists()) {
            properties.load(project.rootProject.file("local.properties").inputStream())
        }
        android.buildFeatures.buildConfig=true
        
        // Config Fields
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
        buildConfigField("String", "CINEMATV_API", "\"${properties.getProperty("CINEMATV_API")}\"")
        buildConfigField("String", "SFMOVIES_API", "\"${properties.getProperty("SFMOVIES_API")}\"")
        buildConfigField("String", "ZSHOW_API", "\"${properties.getProperty("ZSHOW_API")}\"")
        buildConfigField("String", "DUMP_API", "\"${properties.getProperty("DUMP_API")}\"")
        buildConfigField("String", "DUMP_KEY", "\"${properties.getProperty("DUMP_KEY")}\"")
        buildConfigField("String", "CRUNCHYROLL_BASIC_TOKEN", "\"${properties.getProperty("CRUNCHYROLL_BASIC_TOKEN")}\"")
        buildConfigField("String", "CRUNCHYROLL_REFRESH_TOKEN", "\"${properties.getProperty("CRUNCHYROLL_REFRESH_TOKEN")}\"")
        buildConfigField("String", "MOVIE_API", "\"${properties.getProperty("MOVIE_API")}\"")
        buildConfigField("String", "ANICHI_API", "\"${properties.getProperty("ANICHI_API")}\"")
        buildConfigField("String", "Whvx_API", "\"${properties.getProperty("Whvx_API")}\"")
        buildConfigField("String", "CatflixAPI", "\"${properties.getProperty("CatflixAPI")}\"")
        buildConfigField("String", "ConsumetAPI", "\"${properties.getProperty("ConsumetAPI")}\"")
        buildConfigField("String", "FlixHQAPI", "\"${properties.getProperty("FlixHQAPI")}\"")
        buildConfigField("String", "WhvxAPI", "\"${properties.getProperty("WhvxAPI")}\"")
        buildConfigField("String", "WhvxT", "\"${properties.getProperty("WhvxT")}\"")
        buildConfigField("String", "SharmaflixApikey", "\"${properties.getProperty("SharmaflixApikey")}\"")
        buildConfigField("String", "SharmaflixApi", "\"${properties.getProperty("SharmaflixApi")}\"")
        buildConfigField("String", "Theyallsayflix", "\"${properties.getProperty("Theyallsayflix")}\"")
        buildConfigField("String", "GojoAPI", "\"${properties.getProperty("GojoAPI")}\"")
        buildConfigField("String", "HianimeAPI", "\"${properties.getProperty("HianimeAPI")}\"")
        buildConfigField("String", "Vidsrccc", "\"${properties.getProperty("Vidsrccc")}\"")
        buildConfigField("String", "WASMAPI", "\"${properties.getProperty("WASMAPI")}\"")
        buildConfigField("String", "KissKh", "\"${properties.getProperty("KissKh")}\"")
        buildConfigField("String", "KisskhSub", "\"${properties.getProperty("KisskhSub")}\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"${properties.getProperty("SUPERSTREAM_THIRD_API")}\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"${properties.getProperty("SUPERSTREAM_FOURTH_API")}\"")
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"${properties.getProperty("SUPERSTREAM_FIRST_API")}\"")
        buildConfigField("String", "StreamPlayAPI", "\"${properties.getProperty("StreamPlayAPI")}\"")
        buildConfigField("String", "PROXYAPI", "\"${properties.getProperty("PROXYAPI")}\"")
        buildConfigField("String", "KAISVA", "\"${properties.getProperty("KAISVA")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_ALT")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_DEFAULT")}\"")
        buildConfigField("String", "KAIMEG", "\"${properties.getProperty("KAIMEG")}\"")
        buildConfigField("String", "KAIDEC", "\"${properties.getProperty("KAIDEC")}\"")
        buildConfigField("String", "KAIENC", "\"${properties.getProperty("KAIENC")}\"")
        buildConfigField("String", "Nuviostreams", "\"${properties.getProperty("Nuviostreams")}\"")
        buildConfigField("String", "VideasyDEC", "\"${properties.getProperty("VideasyDEC")}\"")
        buildConfigField("String", "YFXENC", "\"${properties.getProperty("YFXENC")}\"")
        buildConfigField("String", "YFXDEC", "\"${properties.getProperty("YFXDEC")}\"")
    }
}

cloudstream {
    language = "en"
    
    description = "Lateri3Play - A lightweight provider for AdiManu"
    authors = listOf("AdiManuLateri3")

    status = 1 
    
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Anime",
        "Movie",
        "Cartoon",
        "AnimeMovie"
    )

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
