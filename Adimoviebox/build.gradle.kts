import com.lagradost.cloudstream3.gradle.CloudstreamExtension

version = 4 // Versi diperbarui karena perubahan besar pada API

cloudstream {
    description = "Nonton sampe biji mata kaluar"
    language    = "id"
    authors = listOf("AdiManuLateri3")
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "Anime", "AsianDrama")
    iconUrl = "https://raw.githubusercontent.com/michat88/Zaneta/refs/heads/main/Icons/adi.png"
    isCrossPlatform = true
}
