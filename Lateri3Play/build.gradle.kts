@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    // CRITICAL FIX: Namespace determines where BuildConfig is generated
    namespace = "com.AdiManuLateri3"
    
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    defaultConfig {
        // API Keys & Constants (Hardcoded based on your dump analysis)
        buildConfigField("String", "TMDB_API", "\"98ae14df2b8d8f8f8136499daf79f0e0\"")
        
        buildConfigField("String", "KissKh", "\"https://kisskh.ovh\"")
        buildConfigField("String", "KisskhSub", "\"https://kisskh.ovh/api/Sub/\"")
        buildConfigField("String", "FlixHQAPI", "\"https://flixhq.to\"")
        
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"https://api.inmoviebox.com\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"https://api.inmoviebox.com\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"https://api.inmoviebox.com\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"DefaultSecretKeyPlaceHolder\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"AltSecretKeyPlaceHolder\"")

        buildConfigField("String", "Vidsrccc", "\"https://vidsrc.cc\"")
        buildConfigField("String", "RidomoviesAPI", "\"https://ridomovies.tv\"")
        
        buildConfigField("String", "KAISVA", "\"https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec\"")
        buildConfigField("String", "KAIMEG", "\"https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec\"")
        
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
