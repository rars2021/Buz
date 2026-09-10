package com.buz.core

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class ImportTest {
    @Test fun csvWithHeaderDipDipDir() {
        val txt = """
            dip,dipdir,quantity
            45,090,1
            60,180,2
            30,270,1
        """.trimIndent()
        val table = CsvFile.parse(StringReader(txt))
        assertEquals(listOf("dip", "dipdir", "quantity"), table.header)
        val ms = CsvFile.toMeasurements(table)
        assertEquals(3, ms.size)
        assertEquals(OrientationType.DIP_DIPDIR, ms[0].type)
        assertEquals(2.0, ms[1].quantity, 1e-9)
    }

    @Test fun csvSemicolonAndCommaDecimals() {
        val txt = """
            trend;plunge
            45,5;30,0
            120,0;60,5
        """.trimIndent()
        val table = CsvFile.parse(StringReader(txt))
        val ms = CsvFile.toMeasurements(table)
        assertEquals(45.5, ms[0].a, 1e-6)
        assertEquals(30.0, ms[0].b, 1e-6)
        assertEquals(OrientationType.TREND_PLUNGE, ms[0].type)
    }

    @Test fun csvFallsBackToPositional() {
        val txt = """
            col1,col2
            10,20
            30,40
        """.trimIndent()
        val table = CsvFile.parse(StringReader(txt))
        val ms = CsvFile.toMeasurements(table)
        assertEquals(2, ms.size)
        assertEquals(10.0, ms[0].a, 1e-9)
    }

    @Test fun terzaghiWeightsInverseOfDot() {
        val axis = ScanlineAxis(Pole.fromTrendPlunge(0.0, 90.0))  // vertical borehole
        val poles = listOf(
            Pole.fromTrendPlunge(0.0, 90.0),   // parallel to axis -> capped weight
            Pole.fromTrendPlunge(0.0, 45.0),   // 45° off -> 1 / sin45 ≈ 1.414
            Pole.fromTrendPlunge(0.0, 0.0)     // perpendicular -> weight -> ∞ (capped)
        )
        val w = Terzaghi.weights(poles, List(3) { axis }, minAngleDeg = 15.0)
        assertTrue(w[0] < w[2])
        assertEquals(1.0 / Math.sin(Math.toRadians(45.0)), w[1], 1e-6)
        val maxCap = 1.0 / Math.sin(Math.toRadians(15.0))
        assertTrue(w[2] <= maxCap + 1e-9)
    }
}
