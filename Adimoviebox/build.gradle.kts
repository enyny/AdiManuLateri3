// use an integer for version numbers
version = 1


cloudstream {

    description = "Nonton sampe biji mata kaluar"
    language    = "id" // Bahasa dari Moviebox
    authors = listOf("AdiManuLateri3")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    // List of video source types. Users are able to filter for extensions in a given category.
    // Anda mendukung semua tipe dari Moviebox
    tvTypes = listOf("Movie","TvSeries","Anime","AsianDrama")

    iconUrl="https://raw.githubusercontent.com/michat88/Zaneta/refs/heads/main/Icons/adi.png"

    isCrossPlatform = true
}
