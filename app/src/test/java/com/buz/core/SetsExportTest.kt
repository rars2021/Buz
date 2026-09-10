package com.buz.core

import org.junit.Assert.*
import org.junit.Test

class SetsExportTest {
    @Test fun poleInsideWindowIsAssigned() {
        val p = Pole.poleOfPlane(dip = 45.0, dipDir = 90.0)
        val (x, y) = Projection.project(p, ProjectionType.EQUAL_AREA)
        val w = SetWindow(0, "A", x - 0.1, y - 0.1, x + 0.1, y + 0.1, ProjectionType.EQUAL_AREA)
        assertTrue(w.contains(p))
    }

    @Test fun assignmentPicksFirstMatchingWindow() {
        val poles = List(5) { Pole.poleOfPlane(45.0, 90.0 + it * 0.5) }
        val (x, y) = Projection.project(poles[0], ProjectionType.EQUAL_AREA)
        val w1 = SetWindow(0, "A", x - 0.2, y - 0.2, x + 0.2, y + 0.2, ProjectionType.EQUAL_AREA)
        val w2 = SetWindow(1, "B", -1.0, -1.0, 1.0, 1.0, ProjectionType.EQUAL_AREA)
        val idx = SetAssignment.assign(poles, listOf(w1, w2))
        assertTrue(idx.all { it == 0 })
    }

    @Test fun csvExportHasHeaderAndRow() {
        val ms = listOf(Measurement(45.0, 90.0, 2.0, 1, emptyList(), OrientationType.DIP_DIPDIR))
        val csv = CsvExport.measurements(ms)
        assertTrue(csv.startsWith("row,a,b,quantity,traverse,dist,type"))
        assertTrue(csv.contains("DIP_DIPDIR"))
    }
}
