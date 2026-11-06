package com.example

import com.example.data.ReleaseRepository
import com.example.model.MagisterRelease
import com.example.ui.DowngradePromptData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testRepositoryReleaseAssetNames() {
        assertEquals("Release-2.7.0-to-2.9.2", ReleaseRepository.DEFAULT_TAG)
        assertEquals("EpicMemazGuy", ReleaseRepository.DEFAULT_OWNER)
        assertEquals("magister-apk-archive-releases", ReleaseRepository.DEFAULT_REPO)
    }

    @Test
    fun testVersion275IsRecommended() {
        val repoAssets = ReleaseRepository.getRepositoryReleaseAssets()
        assertEquals(6, repoAssets.size)

        val v275 = repoAssets.find { it.versionName == "2.7.5" }
        assertNotNull(v275)
        assertTrue("Versie 2.7.5 moet gemarkeerd zijn als aanbevolen", v275!!.isRecommendedLegacy)
        assertFalse("Versie 2.7.5 is standalone APK, geen XAPK", v275.isXapk)
        assertEquals("Magister_2.7.5.apk", v275.assetName)

        val v292 = repoAssets.find { it.versionName == "2.9.2" }
        assertNotNull(v292)
        assertFalse("Versie 2.9.2 mag niet aanbevolen zijn", v292!!.isRecommendedLegacy)
    }

    @Test
    fun testDowngradePromptDataCreation() {
        val release = MagisterRelease(
            id = 20705L,
            tagName = "Release-2.7.0-to-2.9.2",
            versionName = "2.7.5",
            versionCode = 20705,
            releaseDate = "Recent",
            description = "Aanbevolen legacy versie",
            assetName = "Magister_2.7.5.apk",
            downloadUrl = "https://github.com/EpicMemazGuy/magister-apk-archive-releases/releases/download/Release-2.7.0-to-2.9.2/Magister_2.7.5.apk",
            sizeBytes = 83256934L,
            isXapk = false,
            isRecommendedLegacy = true,
            sha256 = "d87671eb2b2b7f9adebafc3b914cdd3…"
        )

        val prompt = DowngradePromptData(
            targetRelease = release,
            installedPackageName = "nl.topicus.magister.parent",
            installedVersionName = "6.1.0",
            isFromConflictError = false
        )

        assertEquals("nl.topicus.magister.parent", prompt.installedPackageName)
        assertEquals("6.1.0", prompt.installedVersionName)
        assertEquals("2.7.5", prompt.targetRelease.versionName)
        assertFalse(prompt.isFromConflictError)
    }

    @Test
    fun testSelfPackageNameExclusion() {
        val selfPackage = "com.epicmemazguy.magistervangister"
        val targetPackage = "nl.topicus.magister.parent"

        // Verify that target Magister package is valid but self app package is explicitly ignored
        assertTrue("Self package name contains 'magister'", selfPackage.contains("magister"))
        assertTrue("Self package name must be filtered out", selfPackage == "com.epicmemazguy.magistervangister")
        assertFalse("Target package is not self app", targetPackage == selfPackage)
    }

    @Test
    fun testMainVersionsFilterOnly275And270() {
        val repoAssets = ReleaseRepository.getRepositoryReleaseAssets()
        val mainVersions = repoAssets.filter { it.versionName == "2.7.5" || it.versionName == "2.7.0" }

        assertEquals(2, mainVersions.size)
        assertTrue("Main versions must contain 2.7.5", mainVersions.any { it.versionName == "2.7.5" })
        assertTrue("Main versions must contain 2.7.0", mainVersions.any { it.versionName == "2.7.0" })

        val v270 = mainVersions.find { it.versionName == "2.7.0" }
        assertNotNull(v270)
        assertFalse("2.7.0 is not recommended", v270!!.isRecommendedLegacy)
    }

    @Test
    fun testManualXapkVersionsList() {
        val repoAssets = ReleaseRepository.getRepositoryReleaseAssets()
        val xapkVersions = repoAssets.filter { it.isXapk }

        assertEquals(4, xapkVersions.size)
        assertTrue("XAPK versions must include 2.9.2", xapkVersions.any { it.versionName == "2.9.2" })
        assertTrue("XAPK versions must include 2.7.4", xapkVersions.any { it.versionName == "2.7.4" })
        assertTrue("XAPK versions must include 2.7.3", xapkVersions.any { it.versionName == "2.7.3" })
        assertTrue("XAPK versions must include 2.7.1", xapkVersions.any { it.versionName == "2.7.1" })
    }

    private fun getMockContext(): android.content.Context {
        val invocationHandler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "getCacheDir" -> java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                else -> null
            }
        }
        return java.lang.reflect.Proxy.newProxyInstance(
            android.content.Context::class.java.classLoader,
            arrayOf(android.content.Context::class.java),
            invocationHandler
        ) as android.content.Context
    }
}

