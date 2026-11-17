package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Streetview
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MapLayerStyle
import com.example.data.WorldCatalog
import com.example.data.WorldLocation
import com.example.data.WorldRepository
import com.example.ui.CameraCommand
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.StreetViewPanoramaOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.streetview.StreetView
import com.google.maps.android.compose.streetview.rememberStreetViewCameraPositionState
import com.google.maps.android.ktx.MapsExperimentalFeature

@Composable
fun CityBuildingsViewport(
    hasValidMapsKey: Boolean,
    selectedLocation: WorldLocation,
    mapLayerStyle: MapLayerStyle,
    cameraCommand: CameraCommand,
    onSurfaceTapped: (Double, Double) -> Unit,
    onOpenAtlas: () -> Unit,
    onRandomFlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasValidMapsKey) {
        Map3DViewport(
            hasValidMapsKey = false,
            selectedLocation = selectedLocation,
            mapLayerStyle = mapLayerStyle,
            cameraCommand = cameraCommand,
            onMapError = {},
            onSurfaceTapped = onSurfaceTapped,
            onOpenAtlas = onOpenAtlas,
            onRandomFlight = onRandomFlight,
            modifier = modifier
        )
        return
    }

    val targetLatLng = remember(selectedLocation.latitude, selectedLocation.longitude) {
        LatLng(selectedLocation.latitude, selectedLocation.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(targetLatLng)
            .zoom(17.2f)
            .tilt(cameraCommand.tilt.toFloat().coerceIn(0f, 67.5f))
            .bearing(cameraCommand.heading.toFloat())
            .build()
    }

    LaunchedEffect(cameraCommand.id) {
        val zoomForRange = when {
            cameraCommand.range < 500 -> 18.2f
            cameraCommand.range < 1000 -> 17.3f
            cameraCommand.range < 2200 -> 16.0f
            else -> 14.5f
        }
        val camPos = CameraPosition.Builder()
            .target(LatLng(cameraCommand.location.latitude, cameraCommand.location.longitude))
            .zoom(zoomForRange)
            .tilt(cameraCommand.tilt.toFloat().coerceIn(0f, 67.5f))
            .bearing(cameraCommand.heading.toFloat())
            .build()
        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(camPos),
            durationMs = 1300
        )
    }

    GoogleMap(
        modifier = modifier
            .fillMaxSize()
            .testTag("city_3d_buildings_map"),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = if (mapLayerStyle == MapLayerStyle.HYBRID) MapType.NORMAL else MapType.HYBRID,
            isBuildingEnabled = true
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            zoomControlsEnabled = false,
            tiltGesturesEnabled = true,
            rotationGesturesEnabled = true
        ),
        onMapClick = { latLng ->
            onSurfaceTapped(latLng.latitude, latLng.longitude)
        }
    ) {
        WorldCatalog.landmarks.forEach { landmark ->
            Marker(
                state = MarkerState(position = LatLng(landmark.latitude, landmark.longitude)),
                title = landmark.name,
                snippet = landmark.country
            )
        }
        if (selectedLocation.isCustomSearch) {
            Marker(
                state = MarkerState(position = targetLatLng),
                title = selectedLocation.name,
                snippet = selectedLocation.country
            )
        }
    }
}

@OptIn(MapsExperimentalFeature::class)
@Composable
fun StreetView360Viewport(
    hasValidMapsKey: Boolean,
    selectedLocation: WorldLocation,
    onSwitchBackTo3D: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { WorldRepository(context) }
    val targetLatLng = remember(selectedLocation.latitude, selectedLocation.longitude) {
        LatLng(selectedLocation.latitude, selectedLocation.longitude)
    }

    var coverageAvailable by remember(targetLatLng) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(targetLatLng, hasValidMapsKey) {
        if (!hasValidMapsKey) {
            coverageAvailable = false
            return@LaunchedEffect
        }
        coverageAvailable = null
        coverageAvailable = repository.checkStreetViewAvailable(
            targetLatLng.latitude,
            targetLatLng.longitude
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("street_view_360_container"),
        contentAlignment = Alignment.Center
    ) {
        when (coverageAvailable) {
            null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.street_view_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            true -> {
                val streetCameraState = rememberStreetViewCameraPositionState()
                LaunchedEffect(targetLatLng) {
                    streetCameraState.setPosition(targetLatLng)
                    streetCameraState.animateTo(
                        StreetViewPanoramaCamera.builder()
                            .zoom(1f)
                            .bearing(selectedLocation.defaultHeadingDegrees.toFloat())
                            .tilt(10f)
                            .build(),
                        durationMs = 800
                    )
                }
                StreetView(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = streetCameraState,
                    streetViewPanoramaOptionsFactory = {
                        StreetViewPanoramaOptions().position(targetLatLng)
                    },
                    isPanningGesturesEnabled = true,
                    isStreetNamesEnabled = true,
                    isUserNavigationEnabled = true
                )
            }
            false -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Streetview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = selectedLocation.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (hasValidMapsKey) {
                                stringResource(R.string.street_view_unavailable)
                            } else {
                                stringResource(R.string.maps_key_required_body)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onSwitchBackTo3D,
                            modifier = Modifier.testTag("street_view_back_to_3d")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewInAr,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.view_mode_3d))
                        }
                    }
                }
            }
        }
    }
}
