package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ViewMode
import com.example.ui.WorldViewModel
import com.example.ui.components.CameraControlsContent
import com.example.ui.components.CityBuildingsViewport
import com.example.ui.components.LocationTelemetryDock
import com.example.ui.components.Map3DViewport
import com.example.ui.components.StreetView360Viewport
import com.example.ui.components.WorldAtlasContent
import com.example.ui.components.WorldTopHud
import com.example.ui.theme.TerraViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TerraViewTheme(darkTheme = true) {
                TerraViewApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerraViewApp(
    viewModel: WorldViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val deniedMessage = stringResource(R.string.location_permission_denied)

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.flyToDeviceLocation()
        } else {
            viewModel.showStatusMessage(deniedMessage)
        }
    }

    val requestLocationAction = {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.flyToDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.showStatusMessage(null)
    }

    BackHandler(
        enabled = uiState.isSearchActive || uiState.isAtlasSheetOpen || uiState.isCameraPanelOpen
    ) {
        when {
            uiState.isSearchActive -> viewModel.setSearchActive(false)
            uiState.isCameraPanelOpen -> viewModel.setCameraPanelOpen(false)
            uiState.isAtlasSheetOpen -> viewModel.setAtlasSheetOpen(false)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 180.dp)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedScreen = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedScreen) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                    ) {
                        WorldAtlasContent(
                            selectedLocation = uiState.selectedLocation,
                            selectedRegion = uiState.selectedRegion,
                            recentLocations = uiState.recentLocations,
                            onSelectRegion = viewModel::selectRegionFilter,
                            onSelectLocation = { viewModel.selectLocation(it, closeOverlays = false) },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Active 3D / City / Street View Viewport
                    when (uiState.viewMode) {
                        ViewMode.PHOTOREALISTIC_3D -> {
                            Map3DViewport(
                                hasValidMapsKey = uiState.hasValidMapsKey,
                                selectedLocation = uiState.selectedLocation,
                                mapLayerStyle = uiState.mapLayerStyle,
                                cameraCommand = uiState.cameraCommand,
                                onMapError = { msg -> viewModel.showStatusMessage(msg) },
                                onSurfaceTapped = { lat, lng ->
                                    viewModel.onMapSurfaceTapped(lat, lng)
                                },
                                onOpenAtlas = { viewModel.setAtlasSheetOpen(true) },
                                onRandomFlight = viewModel::flyToRandomLandmark,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ViewMode.CITY_3D_BUILDINGS -> {
                            CityBuildingsViewport(
                                hasValidMapsKey = uiState.hasValidMapsKey,
                                selectedLocation = uiState.selectedLocation,
                                mapLayerStyle = uiState.mapLayerStyle,
                                cameraCommand = uiState.cameraCommand,
                                onSurfaceTapped = { lat, lng ->
                                    viewModel.onMapSurfaceTapped(lat, lng)
                                },
                                onOpenAtlas = { viewModel.setAtlasSheetOpen(true) },
                                onRandomFlight = viewModel::flyToRandomLandmark,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ViewMode.STREET_VIEW_360 -> {
                            StreetView360Viewport(
                                hasValidMapsKey = uiState.hasValidMapsKey,
                                selectedLocation = uiState.selectedLocation,
                                onSwitchBackTo3D = {
                                    viewModel.setViewMode(ViewMode.PHOTOREALISTIC_3D)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Top Search & Mode HUD
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        WorldTopHud(
                            searchQuery = uiState.searchQuery,
                            isSearchActive = uiState.isSearchActive,
                            isSearching = uiState.isSearching,
                            searchError = uiState.searchError,
                            searchResults = uiState.searchResults,
                            viewMode = uiState.viewMode,
                            mapLayerStyle = uiState.mapLayerStyle,
                            onSearchQueryChange = viewModel::onSearchQueryChanged,
                            onSearchFocusChange = viewModel::setSearchActive,
                            onSearchSubmit = viewModel::submitSearch,
                            onSelectLocation = { viewModel.selectLocation(it) },
                            onSelectViewMode = viewModel::setViewMode,
                            onToggleLayerStyle = viewModel::toggleLayerStyle,
                            onRequestMyLocation = requestLocationAction,
                            onRandomFlight = viewModel::flyToRandomLandmark
                        )
                    }

                    // Bottom Location Telemetry Dock
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        LocationTelemetryDock(
                            location = uiState.selectedLocation,
                            isOrbiting = uiState.isOrbiting,
                            showAtlasButton = !isExpandedScreen,
                            onToggleOrbit = viewModel::toggleOrbit,
                            onFlyToLocation = { viewModel.selectLocation(uiState.selectedLocation) },
                            onOpenCameraPanel = { viewModel.setCameraPanelOpen(true) },
                            onOpenAtlas = { viewModel.setAtlasSheetOpen(true) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isAtlasSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.setAtlasSheetOpen(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WorldAtlasContent(
                selectedLocation = uiState.selectedLocation,
                selectedRegion = uiState.selectedRegion,
                recentLocations = uiState.recentLocations,
                onSelectRegion = viewModel::selectRegionFilter,
                onSelectLocation = { viewModel.selectLocation(it) },
                modifier = Modifier.fillMaxHeight(0.84f)
            )
        }
    }

    if (uiState.isCameraPanelOpen) {
        val cameraSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.setCameraPanelOpen(false) },
            sheetState = cameraSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CameraControlsContent(
                tilt = uiState.cameraTilt,
                heading = uiState.cameraHeading,
                rangeMeters = uiState.cameraRange,
                onUpdateCamera = viewModel::updateCameraParameters,
                onResetNorth = viewModel::resetNorthAndHorizon,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}
