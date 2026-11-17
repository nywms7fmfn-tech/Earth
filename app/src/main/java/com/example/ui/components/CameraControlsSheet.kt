package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.roundToInt

@Composable
fun CameraControlsContent(
    tilt: Float,
    heading: Float,
    rangeMeters: Float,
    onUpdateCamera: (tilt: Float?, heading: Float?, range: Float?) -> Unit,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.camera_controls),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = onResetNorth,
                modifier = Modifier.testTag("reset_north_button")
            ) {
                Text(text = stringResource(R.string.reset_north))
            }
        }

        // Camera Angle Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                label = "Horizon 72°",
                onClick = { onUpdateCamera(72f, null, null) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("preset_horizon")
            )
            PresetChip(
                label = "Isometric 48°",
                onClick = { onUpdateCamera(48f, null, null) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("preset_isometric")
            )
            PresetChip(
                label = "Top-Down 0°",
                onClick = { onUpdateCamera(0f, null, null) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("preset_topdown")
            )
        }

        CameraSliderRow(
            label = stringResource(R.string.tilt_label),
            valueText = "${tilt.roundToInt()}°",
            value = tilt,
            valueRange = 0f..85f,
            onValueChange = { onUpdateCamera(it, null, null) },
            testTag = "tilt_slider"
        )

        CameraSliderRow(
            label = stringResource(R.string.heading_label),
            valueText = "${heading.roundToInt()}°",
            value = heading,
            valueRange = 0f..360f,
            onValueChange = { onUpdateCamera(null, it, null) },
            testTag = "heading_slider"
        )

        CameraSliderRow(
            label = stringResource(R.string.range_label),
            valueText = "${rangeMeters.roundToInt()} m",
            value = rangeMeters,
            valueRange = 220f..5000f,
            onValueChange = { onUpdateCamera(null, null, it) },
            testTag = "range_slider"
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PresetChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CameraSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
