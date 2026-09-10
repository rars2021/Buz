package com.buz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import kotlin.math.abs

class CoreTest {
    @Test fun horizontalPlaneHasVerticalPole() {
        val p = Pole.poleOfPlane(dip = 0.0, dipDir = 90.0)
        assertEquals(90.0, p.plunge, 1e-6)
    }

    @Test fun verticalPlaneHasHorizontalPole() {
        val p = Pole.poleOfPlane(dip = 90.0, dipDir = 45.0)
        assertEquals(0.0, p.plunge, 1e-6)
        // Pole direction is opposite to dip direction => 225°
        assertEquals(225.0, p.trend, 1e-6)
    }

    @Test fun equalAreaMapsVerticalToCentre() {
        val p = Pole.fromTrendPlunge(30.0, 90.0)
        val (x, y) = Projection.project(p, ProjectionType.EQUAL_AREA)
        assertTrue(abs(x) < 1e-9 && abs(y) < 1e-9)
    }

    @Test fun equalAreaMapsHorizontalToUnitCircle() {
        val p = Pole.fromTrendPlunge(0.0, 0.0)  // due north, horizontal
        val (x, y) = Projection.project(p, ProjectionType.EQUAL_AREA)
        assertEquals(0.0, x, 1e-6)
        assertEquals(1.0, y, 1e-6)
    }

    @Test fun fisherMeanConcentratedCluster() {
        val poles = (1..20).map { Pole.fromTrendPlunge(45.0 + (it - 10) * 0.5, 60.0 + (it - 10) * 0.5) }
        val r = Fisher.analyse(poles)!!
        assertTrue(r.k > 100)
        assertEquals(45.0, r.mean.trend, 2.0)
        assertEquals(60.0, r.mean.plunge, 2.0)
    }

    @Test fun parseMinimalDipFile() {
        val txt = """
            My project
            Data collector
            0 traverses
            0
            0
            0
            45 090
            60 180
            30 270 2.0
        """.trimIndent()
        val data = DipFile.parse(StringReader(txt))
        assertEquals(3, data.measurements.size)
        assertEquals(45.0, data.measurements[0].a, 1e-9)
        assertEquals(90.0, data.measurements[0].b, 1e-9)
        assertEquals(OrientationType.DIP_DIPDIR, data.header.orientationType)
    }
}
