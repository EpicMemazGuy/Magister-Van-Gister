package com.example

import com.example.model.DownloadState
import com.example.model.MagisterRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun testMagisterReleaseMetadata() {
        val release = MagisterRelease(
            id = 20902L,
            tagName = "Release-2.7.0-to-2.9.2",
            versionName = "2.9.2",
            versionCode = 20902,
            releaseDate = "2024-05-18",
            description = "Laatste stabiele legacy release",
            assetName = "magister.xapk",
            downloadUrl = "https://github.com/EpicMemazGuy/magister-apk-archive-releases",
            sizeBytes = 44040192L,
            isXapk = true,
            isRecommendedLegacy = true
        )

        assertEquals("2.9.2", release.versionName)
        assertEquals(20902, release.versionCode)
        assertTrue(release.isXapk)
        assertTrue(release.isRecommendedLegacy)
        assertEquals("v2.x", release.majorEra)
        assertEquals("42.0 MB", release.formattedSize)
    }

    @Test
    fun testDownloadStatePercentageCalculation() {
        val state = DownloadState.Downloading(
            releaseId = 20902L,
            progress = 0.75f,
            downloadedBytes = 7500000L,
            totalBytes = 10000000L
        )

        assertEquals(75, state.percentage)
        assertTrue(state.formattedProgress.contains("75%"))
    }
}
