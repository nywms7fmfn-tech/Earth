package com.example.ui.components

import android.os.Bundle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.MapLayerStyle
import com.example.data.WorldCatalog
import com.example.data.WorldLocation
import com.example.ui.CameraActionType
import com.example.ui.CameraCommand
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DInitConfig
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.AltitudeMode
import com.google.android.gms.maps3d.model.Map3DMode
import com.google.android.gms.maps3d.model.camera
import com.google.android.gms.maps3d.model.flyAroundOptions
import com.google.android.gms.maps3d.model.flyToOptions
import com.google.android.gms.maps3d.model.latLngAltitude
import com.google.android.gms.maps3d.model.markerOptions
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Map3DViewport(
    hasValidMapsKey: Boolean,
    selectedLocation: WorldLocation,
    mapLayerStyle: MapLayerStyle,
    cameraCommand: CameraCommand,
    onMapError: (String) -> Unit,
    onSurfaceTapped: (Double, Double) -> Unit,
    onOpenAtlas: () -> Unit,
    onRandomFlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasValidMapsKey) {
        OrbitalGlobePreviewFallback(
            selectedLocation = selectedLocation,
            onOpenAtlas = onOpenAtlas,
            onRandomFlight = onRandomFlight,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var googleMap3D by remember { mutableStateOf<GoogleMap3D?>(null) }
    var lastExecutedCommandId by remember { mutableStateOf(-1L) }

    val map3DView = remember {
        val initialConfig = Map3DInitConfig.create(
            mapMode = if (mapLayerStyle == MapLayerStyle.HYBRID) {
                Map3DMode.HYBRID
            } else {
                Map3DMode.SATELLITE
            },
            centerLat = selectedLocation.latitude,
            centerLng = selectedLocation.longitude
        )
        Map3DView(context, initialConfig).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycleOwner, map3DView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> map3DView.onStart()
                Lifecycle.Event.ON_RESUME -> map3DView.onResume()
                Lifecycle.Event.ON_PAUSE -> map3DView.onPause()
                Lifecycle.Event.ON_STOP -> map3DView.onStop()
                Lifecycle.Event.ON_DESTROY -> map3DView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        map3DView.getMap3DViewAsync(object : OnMap3DViewReadyCallback {
            override fun onMap3DViewReady(map: GoogleMap3D) {
                googleMap3D = map
                map.setMapMode(
                    if (mapLayerStyle == MapLayerStyle.HYBRID) {
                        Map3DMode.HYBRID
                    } else {
                        Map3DMode.SATELLITE
                    }
                )
                WorldCatalog.landmarks.forEach { landmark ->
                    map.addMarker(
                        markerOptions {
                            position = latLngAltitude {
                                latitude = landmark.latitude
                                longitude = landmark.longitude
                                altitude = landmark.altitudeMeters
                            }
                            altitudeMode = AltitudeMode.RELATIVE_TO_GROUND
                            label = landmark.name
                            isExtruded = true
                        }
                    )
                }
                map.setMap3DClickListener { event ->
                    event.location?.let { loc ->
                        onSurfaceTapped(loc.latitude, loc.longitude)
                    }
                    false
                }
            }

            override fun onError(error: Exception) {
                onMapError(error.localizedMessage ?: "Unable to initialize 3D map tiles.")
            }
        })

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map3DView.onPause()
            map3DView.onStop()
            map3DView.onDestroy()
        }
    }

    LaunchedEffect(googleMap3D, mapLayerStyle) {
        val map = googleMap3D ?: return@LaunchedEffect
        map.setMapMode(
            if (mapLayerStyle == MapLayerStyle.HYBRID) {
                Map3DMode.HYBRID
            } else {
                Map3DMode.SATELLITE
            }
        )
    }

    LaunchedEffect(googleMap3D, cameraCommand.id) {
        val map = googleMap3D ?: return@LaunchedEffect
        if (lastExecutedCommandId == cameraCommand.id) return@LaunchedEffect
        lastExecutedCommandId = cameraCommand.id

        val targetCamera = camera {
            center = latLngAltitude {
                latitude = cameraCommand.location.latitude
                longitude = cameraCommand.location.longitude
                altitude = cameraCommand.location.altitudeMeters
            }
            heading = cameraCommand.heading
            tilt = cameraCommand.tilt
            range = cameraCommand.range
        }

        when (cameraCommand.type) {
            CameraActionType.FLY_TO -> {
                map.flyCameraTo(
                    flyToOptions {
                        endCamera = targetCamera
                        durationInMillis = 2200L
                    }
                )
            }
            CameraActionType.INSTANT_ADJUST -> {
                map.setCamera(targetCamera)
            }
            CameraActionType.ORBIT_AROUND -> {
                map.flyCameraAround(
                    flyAroundOptions {
                        center = targetCamera
                        durationInMillis = 18000L
                        rounds = 2.0
                    }
                )
            }
            CameraActionType.STOP_ORBIT -> {
                map.stopCameraAnimation()
            }
        }
    }

    AndroidView(
        factory = { map3DView },
        modifier = modifier
            .fillMaxSize()
            .testTag("photorealistic_3d_map")
    )
}

@Composable
private fun OrbitalGlobePreviewFallback(
    selectedLocation: WorldLocation,
    onOpenAtlas: () -> Unit,
    onRandomFlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "globe_orbit")
    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_phase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_world_atlas_hero),
            contentDescription = "Earth horizon from orbit",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.32f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.88f),
                            backgroundColor.copy(alpha = 0.45f),
                            backgroundColor.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        // Interactive orbital wireframe telemetry canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val radius = (minOf(size.width, size.height) * 0.34f).coerceAtMost(240f)

            // Outer atmosphere glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.45f
                ),
                radius = radius * 1.45f,
                center = center
            )

            // Globe sphere rim & lat/lng grid arcs
            drawCircle(
                color = primaryColor.copy(alpha = 0.42f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.5f)
            )

            for (ring in 1..3) {
                val factor = ring / 4f
                drawCircle(
                    color = primaryColor.copy(alpha = 0.14f),
                    radius = radius * factor,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // Orbiting satellite indicator
            val rad = Math.toRadians(orbitPhase.toDouble())
            val satX = center.x + (radius * 1.22f * cos(rad)).toFloat()
            val satY = center.y + (radius * 0.46f * sin(rad)).toFloat()
            drawCircle(
                color = primaryColor,
                radius = 6f,
                center = Offset(satX, satY)
            )

            // Selected landmark projected onto globe hemisphere
            val lngShift = ((selectedLocation.longitude / 180.0) * radius * 0.72).toFloat()
            val latShift = ((-selectedLocation.latitude / 90.0) * radius * 0.72).toFloat()
            val pinPos = Offset(center.x + lngShift, center.y + latShift)

            drawCircle(
                color = secondaryColor.copy(alpha = 0.28f * pulse),
                radius = 28f * pulse,
                center = pinPos
            )
            drawCircle(
                color = secondaryColor,
                radius = 9f,
                center = pinPos
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 140.dp)
                .widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.maps_key_required_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.maps_key_required_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRandomFlight,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fallback_surprise_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(text = stringResource(R.string.random_destination))
                        }
                        Button(
                            onClick = onOpenAtlas,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fallback_atlas_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(text = stringResource(R.string.explore_atlas))
                        }
                    }
                }
            }
        }
    }
}
