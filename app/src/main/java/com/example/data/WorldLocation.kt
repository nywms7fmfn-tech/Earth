package com.example.data

enum class WorldRegion(val displayName: String) {
    EUROPE("Europe"),
    ASIA("Asia"),
    AMERICAS("Americas"),
    MIDDLE_EAST_AFRICA("Middle East & Africa"),
    OCEANIA("Oceania")
}

enum class LandmarkCategory(val displayName: String) {
    ICONIC_SKYLINE("Skyline & City"),
    ANCIENT_WONDER("Ancient Wonder"),
    NATURAL_MARVEL("Natural Marvel"),
    CULTURAL_ICON("Cultural Icon")
}

enum class ViewMode {
    PHOTOREALISTIC_3D,
    CITY_3D_BUILDINGS,
    STREET_VIEW_360
}

enum class MapLayerStyle {
    HYBRID,
    SATELLITE
}

data class WorldLocation(
    val id: String,
    val name: String,
    val country: String,
    val region: WorldRegion,
    val category: LandmarkCategory,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val defaultRangeMeters: Double,
    val defaultTiltDegrees: Double,
    val defaultHeadingDegrees: Double,
    val description: String,
    val highlightTag: String,
    val isCustomSearch: Boolean = false
) {
    fun formattedCoordinates(): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lngDir = if (longitude >= 0) "E" else "W"
        return String.format(
            java.util.Locale.US,
            "%.4f°%s  %.4f°%s",
            kotlin.math.abs(latitude),
            latDir,
            kotlin.math.abs(longitude),
            lngDir
        )
    }
}
