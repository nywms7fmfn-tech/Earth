package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.WorldCatalog
import com.example.data.WorldRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TerraView 3D", appName)
    }

    @Test
    fun `world catalog covers all regions with valid coordinates`() {
        val regionsCovered = WorldCatalog.landmarks.map { it.region }.toSet()
        assertEquals(WorldRegion.entries.toSet(), regionsCovered)
        assertTrue(WorldCatalog.landmarks.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
    }
}
