package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "status") val status: String,
    @Json(name = "results") val results: List<GeocodingResult> = emptyList(),
    @Json(name = "error_message") val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    @Json(name = "place_id") val placeId: String = "",
    @Json(name = "formatted_address") val formattedAddress: String = "",
    @Json(name = "geometry") val geometry: GeocodingGeometry,
    @Json(name = "address_components") val addressComponents: List<AddressComponent> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeocodingGeometry(
    @Json(name = "location") val location: GeoLatLng
)

@JsonClass(generateAdapter = true)
data class GeoLatLng(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lng") val lng: Double
)

@JsonClass(generateAdapter = true)
data class AddressComponent(
    @Json(name = "long_name") val longName: String,
    @Json(name = "types") val types: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StreetViewMetadataResponse(
    @Json(name = "status") val status: String
)

interface GeocodingService {
    @GET("maps/api/geocode/json")
    suspend fun geocodeAddress(
        @Query("address") query: String,
        @Query("key") apiKey: String
    ): GeocodingResponse

    @GET("maps/api/geocode/json")
    suspend fun reverseGeocode(
        @Query("latlng") latLng: String,
        @Query("key") apiKey: String
    ): GeocodingResponse

    @GET("maps/api/streetview/metadata")
    suspend fun checkStreetViewMetadata(
        @Query("location") location: String,
        @Query("key") apiKey: String
    ): StreetViewMetadataResponse
}
