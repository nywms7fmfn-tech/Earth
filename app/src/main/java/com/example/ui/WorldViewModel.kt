package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LandmarkCategory
import com.example.data.MapLayerStyle
import com.example.data.SearchOutcome
import com.example.data.ViewMode
import com.example.data.WorldCatalog
import com.example.data.WorldLocation
import com.example.data.WorldRegion
import com.example.data.WorldRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CameraActionType {
    FLY_TO,
    INSTANT_ADJUST,
    ORBIT_AROUND,
    STOP_ORBIT
}

data class CameraCommand(
    val id: Long,
    val type: CameraActionType,
    val location: WorldLocation,
    val tilt: Double,
    val heading: Double,
    val range: Double
)

data class WorldUiState(
    val selectedLocation: WorldLocation = WorldCatalog.landmarks.first(),
    val viewMode: ViewMode = ViewMode.PHOTOREALISTIC_3D,
    val mapLayerStyle: MapLayerStyle = MapLayerStyle.HYBRID,
    val cameraTilt: Float = WorldCatalog.landmarks.first().defaultTiltDegrees.toFloat(),
    val cameraHeading: Float = WorldCatalog.landmarks.first().defaultHeadingDegrees.toFloat(),
    val cameraRange: Float = WorldCatalog.landmarks.first().defaultRangeMeters.toFloat(),
    val isOrbiting: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<WorldLocation> = WorldCatalog.landmarks,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedRegion: WorldRegion? = null,
    val isAtlasSheetOpen: Boolean = false,
    val isCameraPanelOpen: Boolean = false,
    val hasValidMapsKey: Boolean = false,
    val isMapsKeyNoticeDismissed: Boolean = false,
    val recentLocations: List<WorldLocation> = listOf(WorldCatalog.landmarks.first()),
    val statusBanner: String? = null,
    val cameraCommand: CameraCommand = CameraCommand(
        id = 1L,
        type = CameraActionType.FLY_TO,
        location = WorldCatalog.landmarks.first(),
        tilt = WorldCatalog.landmarks.first().defaultTiltDegrees,
        heading = WorldCatalog.landmarks.first().defaultHeadingDegrees,
        range = WorldCatalog.landmarks.first().defaultRangeMeters
    )
)

class WorldViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorldRepository(application.applicationContext)
    private var searchJob: Job? = null
    private var commandCounter = 1L

    private val _uiState = MutableStateFlow(
        WorldUiState(hasValidMapsKey = repository.hasValidMapsKey())
    )
    val uiState: StateFlow<WorldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedLayer = repository.preferredLayerStyle.first()
            val savedLandmarkId = repository.lastLandmarkId.first()
            val initialLocation = WorldCatalog.findById(savedLandmarkId)
            commandCounter++
            _uiState.update { state ->
                state.copy(
                    mapLayerStyle = savedLayer,
                    selectedLocation = initialLocation,
                    cameraTilt = initialLocation.defaultTiltDegrees.toFloat(),
                    cameraHeading = initialLocation.defaultHeadingDegrees.toFloat(),
                    cameraRange = initialLocation.defaultRangeMeters.toFloat(),
                    recentLocations = listOf(initialLocation),
                    cameraCommand = CameraCommand(
                        id = commandCounter,
                        type = CameraActionType.FLY_TO,
                        location = initialLocation,
                        tilt = initialLocation.defaultTiltDegrees,
                        heading = initialLocation.defaultHeadingDegrees,
                        range = initialLocation.defaultRangeMeters
                    )
                )
            }
        }
    }

    fun selectLocation(location: WorldLocation, closeOverlays: Boolean = true) {
        commandCounter++
        val updatedRecent = (listOf(location) + _uiState.value.recentLocations)
            .distinctBy { it.id }
            .take(8)
        _uiState.update { state ->
            state.copy(
                selectedLocation = location,
                cameraTilt = location.defaultTiltDegrees.toFloat(),
                cameraHeading = location.defaultHeadingDegrees.toFloat(),
                cameraRange = location.defaultRangeMeters.toFloat(),
                isOrbiting = false,
                isAtlasSheetOpen = if (closeOverlays) false else state.isAtlasSheetOpen,
                isSearchActive = if (closeOverlays) false else state.isSearchActive,
                recentLocations = updatedRecent,
                statusBanner = null,
                cameraCommand = CameraCommand(
                    id = commandCounter,
                    type = CameraActionType.FLY_TO,
                    location = location,
                    tilt = location.defaultTiltDegrees,
                    heading = location.defaultHeadingDegrees,
                    range = location.defaultRangeMeters
                )
            )
        }
        if (!location.isCustomSearch) {
            viewModelScope.launch {
                repository.saveLastLandmarkId(location.id)
            }
        }
    }

    fun flyToRandomLandmark() {
        val currentId = _uiState.value.selectedLocation.id
        val candidates = WorldCatalog.landmarks.filter { it.id != currentId }
        val next = candidates.randomOrNull() ?: WorldCatalog.landmarks.first()
        selectLocation(next, closeOverlays = true)
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { state ->
            state.copy(
                viewMode = mode,
                isOrbiting = false,
                statusBanner = null
            )
        }
    }

    fun toggleLayerStyle() {
        val nextStyle = if (_uiState.value.mapLayerStyle == MapLayerStyle.HYBRID) {
            MapLayerStyle.SATELLITE
        } else {
            MapLayerStyle.HYBRID
        }
        _uiState.update { it.copy(mapLayerStyle = nextStyle) }
        viewModelScope.launch {
            repository.saveLayerStyle(nextStyle)
        }
    }

    fun toggleOrbit() {
        val current = _uiState.value
        commandCounter++
        if (current.isOrbiting) {
            _uiState.update {
                it.copy(
                    isOrbiting = false,
                    cameraCommand = CameraCommand(
                        id = commandCounter,
                        type = CameraActionType.STOP_ORBIT,
                        location = current.selectedLocation,
                        tilt = current.cameraTilt.toDouble(),
                        heading = current.cameraHeading.toDouble(),
                        range = current.cameraRange.toDouble()
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    viewMode = ViewMode.PHOTOREALISTIC_3D,
                    isOrbiting = true,
                    cameraCommand = CameraCommand(
                        id = commandCounter,
                        type = CameraActionType.ORBIT_AROUND,
                        location = current.selectedLocation,
                        tilt = current.cameraTilt.toDouble(),
                        heading = current.cameraHeading.toDouble(),
                        range = current.cameraRange.toDouble()
                    )
                )
            }
        }
    }

    fun updateCameraParameters(tilt: Float? = null, heading: Float? = null, range: Float? = null) {
        val current = _uiState.value
        val newTilt = (tilt ?: current.cameraTilt).coerceIn(0f, 85f)
        val newHeading = ((heading ?: current.cameraHeading) % 360f + 360f) % 360f
        val newRange = (range ?: current.cameraRange).coerceIn(220f, 5000f)
        commandCounter++
        _uiState.update {
            it.copy(
                cameraTilt = newTilt,
                cameraHeading = newHeading,
                cameraRange = newRange,
                isOrbiting = false,
                cameraCommand = CameraCommand(
                    id = commandCounter,
                    type = CameraActionType.INSTANT_ADJUST,
                    location = current.selectedLocation,
                    tilt = newTilt.toDouble(),
                    heading = newHeading.toDouble(),
                    range = newRange.toDouble()
                )
            )
        }
    }

    fun resetNorthAndHorizon() {
        val loc = _uiState.value.selectedLocation
        commandCounter++
        _uiState.update {
            it.copy(
                cameraTilt = loc.defaultTiltDegrees.toFloat(),
                cameraHeading = 0f,
                cameraRange = loc.defaultRangeMeters.toFloat(),
                isOrbiting = false,
                cameraCommand = CameraCommand(
                    id = commandCounter,
                    type = CameraActionType.FLY_TO,
                    location = loc,
                    tilt = loc.defaultTiltDegrees,
                    heading = 0.0,
                    range = loc.defaultRangeMeters
                )
            )
        }
    }

    fun onMapSurfaceTapped(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val tappedLocation = repository.createLocationFromTap(latitude, longitude)
            selectLocation(tappedLocation, closeOverlays = false)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = true,
                searchError = null
            )
        }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchResults = filterByRegion(WorldCatalog.landmarks, it.selectedRegion),
                    isSearching = false,
                    searchError = null
                )
            }
            return
        }

        // Immediate local preview while debouncing remote lookup
        val instantLocal = WorldCatalog.landmarks.filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                it.country.contains(query.trim(), ignoreCase = true) ||
                it.region.displayName.contains(query.trim(), ignoreCase = true)
        }
        _uiState.update { it.copy(searchResults = instantLocal) }

        searchJob = viewModelScope.launch {
            delay(450L)
            executeSearch(query)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        viewModelScope.launch {
            executeSearch(_uiState.value.searchQuery)
        }
    }

    private suspend fun executeSearch(query: String) {
        if (query.isBlank()) return
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        when (val outcome = repository.searchGlobalLocations(query)) {
            is SearchOutcome.Success -> {
                _uiState.update {
                    it.copy(
                        searchResults = outcome.results,
                        isSearching = false,
                        searchError = null
                    )
                }
            }
            is SearchOutcome.Error -> {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = outcome.message
                    )
                }
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
    }

    fun selectRegionFilter(region: WorldRegion?) {
        _uiState.update { state ->
            state.copy(
                selectedRegion = region,
                searchResults = if (state.searchQuery.isBlank()) {
                    filterByRegion(WorldCatalog.landmarks, region)
                } else {
                    state.searchResults
                }
            )
        }
    }

    private fun filterByRegion(list: List<WorldLocation>, region: WorldRegion?): List<WorldLocation> {
        return if (region == null) list else list.filter { it.region == region }
    }

    fun setAtlasSheetOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isAtlasSheetOpen = open,
                isCameraPanelOpen = if (open) false else it.isCameraPanelOpen
            )
        }
    }

    fun setCameraPanelOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isCameraPanelOpen = open,
                isAtlasSheetOpen = if (open) false else it.isAtlasSheetOpen
            )
        }
    }

    fun dismissMapsKeyNotice() {
        _uiState.update { it.copy(isMapsKeyNoticeDismissed = true) }
    }

    fun showStatusMessage(message: String?) {
        _uiState.update { it.copy(statusBanner = message) }
    }

    @SuppressLint("MissingPermission")
    fun flyToDeviceLocation() {
        val context = getApplication<Application>().applicationContext
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    viewModelScope.launch {
                        val userLoc = repository.createLocationFromTap(
                            location.latitude,
                            location.longitude
                        ).copy(
                            id = "my_location",
                            name = "Your Location",
                            category = LandmarkCategory.ICONIC_SKYLINE,
                            highlightTag = "GPS Position"
                        )
                        selectLocation(userLoc, closeOverlays = true)
                    }
                } else {
                    showStatusMessage("Current GPS location is unavailable right now.")
                }
            }
            .addOnFailureListener {
                showStatusMessage("Unable to fetch device location.")
            }
    }
}
