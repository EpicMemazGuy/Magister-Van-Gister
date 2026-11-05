package com.example

import com.example.data.ReleaseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testRepositoryReleaseAssetNames() {
        // Test that default tag is Release-2.7.0-to-2.9.2
        assertEquals("Release-2.7.0-to-2.9.2", ReleaseRepository.DEFAULT_TAG)
    }

    @Test
    fun testBasicAddition() {
        assertEquals(4, 2 + 2)
    }
}
