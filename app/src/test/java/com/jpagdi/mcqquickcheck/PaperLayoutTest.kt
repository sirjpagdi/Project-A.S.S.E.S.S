package com.jpagdi.mcqquickcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperLayoutTest {

    @Test
    fun removedLayoutsAreRecognizedAsLegacyNames() {
        assertTrue(PaperLayout.LEGACY_LAYOUT_NAMES.contains("HALF_CROSS"))
        assertTrue(PaperLayout.LEGACY_LAYOUT_NAMES.contains("HALF_LENGTH"))
    }

    @Test
    fun currentLayoutNameResolvesToQuarter() {
        assertEquals(PaperLayout.QUARTER, PaperLayout.fromStorageName("QUARTER"))
    }
}
