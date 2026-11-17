package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private val Context.worldPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "terraview_prefs")

sealed class SearchOutcome {
    data class Success(val results: List<WorldLocation>) : SearchOutcome()
    data class Error(val message: String) : SearchOutcome()
}

class WorldRepository(private val context: Context) {

    private val layerKey = stringPreferencesKey("map_layer_style")
    private val lastLandmarkKey = stringPreferencesKey("last_landmark_id")

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val geocodingService: GeocodingService = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeocodingService::class.java)

    val preferredLayerStyle: Flow<MapLayerStyle> = context.worldPrefsDataStore.data.map { prefs ->
        when (prefs[layerKey]) {
            MapLayerStyle.SATELLITE.name -> MapLayerStyle.SATELLITE
            else -> MapLayerStyle.HYBRID
        }
    }

    val lastLandmarkId: Flow<String?> = context.worldPrefsDataStore.data.map { prefs ->
        prefs[lastLandmarkKey]
    }

    suspend fun saveLayerStyle(style: MapLayerStyle) {
        context.worldPrefsDataStore.edit { prefs ->
            prefs[layerKey] = style.name
        }
    }

    suspend fun saveLastLandmarkId(id: String) {
        context.worldPrefsDataStore.edit { prefs ->
            prefs[lastLandmarkKey] = id
        }
    }

    fun hasValidMapsKey(): Boolean {
        val key = BuildConfig.MAPS_API_KEY
        return key.isNotBlank() && key != "MY_MAPS_API_KEY"
    }

    suspend fun checkStreetViewAvailable(lat: Double, lng: Double): Boolean {
        if (!hasValidMapsKey()) return false
        return try {
            val response = geocodingService.checkStreetViewMetadata(
                location = "$lat,$lng",
                apiKey = BuildConfig.MAPS_API_KEY
            )
            response.status == "OK"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun searchGlobalLocations(query: String): SearchOutcome {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return SearchOutcome.Success(WorldCatalog.landmarks)
        }

        // 1. Check direct coordinate input: "48.8584, 2.2945"
        parseCoordinates(trimmed)?.let { (lat, lng) ->
            val coordLocation = WorldLocation(
                id = "coord_${lat}_${lng}",
                name = "Pinned Coordinates",
                country = String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng),
                region = inferRegion(lat, lng),
                category = LandmarkCategory.NATURAL_MARVEL,
                latitude = lat,
                longitude = lng,
                altitudeMeters = 100.0,
                defaultRangeMeters = 900.0,
                defaultTiltDegrees = 66.0,
                defaultHeadingDegrees = 0.0,
                description = "Direct coordinate lock at $lat, $lng.",
                highlightTag = "Direct Coordinate Lock",
                isCustomSearch = true
            )
            return SearchOutcome.Success(listOf(coordLocation))
        }

        // 2. Local curated catalog matches first
        val localMatches = WorldCatalog.landmarks.filter { landmark ->
            landmark.name.contains(trimmed, ignoreCase = true) ||
                landmark.country.contains(trimmed, ignoreCase = true) ||
                landmark.region.displayName.contains(trimmed, ignoreCase = true) ||
                landmark.highlightTag.contains(trimmed, ignoreCase = true)
        }

        // 3. Query Google Maps Geocoding API if key is configured
        if (!hasValidMapsKey()) {
            return if (localMatches.isNotEmpty()) {
                SearchOutcome.Success(localMatches)
            } else {
                SearchOutcome.Error("No curated match found. Configure MAPS_API_KEY in AI Studio Secrets for live worldwide address search.")
            }
        }

        return try {
            val response = geocodingService.geocodeAddress(
                query = trimmed,
                apiKey = BuildConfig.MAPS_API_KEY
            )
            val remoteResults = if (response.status == "OK") {
                response.results.take(6).mapIndexed { index, result ->
                    val shortTitle = result.addressComponents.firstOrNull()?.longName
                        ?: result.formattedAddress.substringBefore(",")
                    val subtitle = result.formattedAddress
                    WorldLocation(
                        id = "geo_${result.placeId.ifBlank { index.toString() }}",
                        name = shortTitle,
                        country = subtitle,
                        region = inferRegion(result.geometry.location.lat, result.geometry.location.lng),
                        category = LandmarkCategory.ICONIC_SKYLINE,
                        latitude = result.geometry.location.lat,
                        longitude = result.geometry.location.lng,
                        altitudeMeters = 80.0,
                        defaultRangeMeters = 950.0,
                        defaultTiltDegrees = 66.0,
                        defaultHeadingDegrees = 15.0,
                        description = "Live global search destination at $subtitle.",
                        highlightTag = "Global Search",
                        isCustomSearch = true
                    )
                }
            } else {
                emptyList()
            }

            val combined = (remoteResults + localMatches).distinctBy {
                "${"%.3f".format(java.util.Locale.US, it.latitude)}_${"%.3f".format(java.util.Locale.US, it.longitude)}"
            }

            if (combined.isNotEmpty()) {
                SearchOutcome.Success(combined)
            } else {
                val apiMsg = response.errorMessage ?: "No matching locations found for \"$trimmed\"."
                SearchOutcome.Error(apiMsg)
            }
        } catch (e: Exception) {
            if (localMatches.isNotEmpty()) {
                SearchOutcome.Success(localMatches)
            } else {
                SearchOutcome.Error(e.localizedMessage ?: "Unable to reach Google Geocoding service.")
            }
        }
    }

    suspend fun createLocationFromTap(lat: Double, lng: Double): WorldLocation {
        val fallback = WorldLocation(
            id = "tap_${System.currentTimeMillis()}",
            name = "3D Surface Point",
            country = String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng),
            region = inferRegion(lat, lng),
            category = LandmarkCategory.NATURAL_MARVEL,
            latitude = lat,
            longitude = lng,
            altitudeMeters = 80.0,
            defaultRangeMeters = 750.0,
            defaultTiltDegrees = 65.0,
            defaultHeadingDegrees = 0.0,
            description = "Selected directly on the 3D globe surface.",
            highlightTag = "Tapped Location",
            isCustomSearch = true
        )
        if (!hasValidMapsKey()) return fallback
        return try {
            val response = geocodingService.reverseGeocode(
                latLng = "$lat,$lng",
                apiKey = BuildConfig.MAPS_API_KEY
            )
            val top = response.results.firstOrNull() ?: return fallback
            val title = top.addressComponents.firstOrNull {
                it.types.contains("point_of_interest") ||
                    it.types.contains("landmark") ||
                    it.types.contains("locality") ||
                    it.types.contains("route")
            }?.longName ?: top.formattedAddress.substringBefore(",")

            fallback.copy(
                name = title,
                country = top.formattedAddress,
                description = "Tapped 3D point near ${top.formattedAddress}."
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseCoordinates(input: String): Pair<Double, Double>? {
        val parts = input.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    private fun inferRegion(lat: Double, lng: Double): WorldRegion {
        return when {
            lng < -30.0 -> WorldRegion.AMERICAS
            lng in -30.0..50.0 && lat >= 35.0 -> WorldRegion.EUROPE
            lng in -20.0..60.0 && lat < 35.0 -> WorldRegion.MIDDLE_EAST_AFRICA
            lng > 110.0 && lat < -10.0 -> WorldRegion.OCEANIA
            else -> WorldRegion.ASIA
        }
    }
}
