package com.jpagdi.mcqquickcheck

import org.junit.Assert.assertEquals
import org.junit.Test

class AlignmentAnalyzerTest {

    @Test
    fun orderQuadPointsForExpectedKeepsCornerOrderConsistent() {
        val analyzer = AlignmentAnalyzer(640, 480, PaperLayout.HALF_CROSS) {}
        val expected = listOf(
            10 to 20,
            90 to 20,
            90 to 80,
            10 to 80
        )
        val unorderedQuad = listOf(
            90 to 80,
            10 to 20,
            90 to 20,
            10 to 80
        )

        val ordered = analyzer.orderQuadPointsForExpected(expected, unorderedQuad)

        assertEquals(expected, ordered)
    }
}
