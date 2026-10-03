package com.rk.taskmanager.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the widget's ordering and formatting, which are the parts most likely to regress
 * silently. These are deliberately free of Android dependencies so they run as plain JVM tests.
 */
class WidgetSelectionTest {

    private fun row(pid: Int, label: String, cpu: Float, ramKb: Long) =
        WidgetProcRow(pid, "pkg.$pid", label, cpu, ramKb, isApp = true)

    private val rows = listOf(
        row(1, "alpha", 5f, 100L),
        row(2, "Bravo", 50f, 20L),
        row(3, "charlie", 1f, 900L),
        row(4, "Delta", 30f, 50L),
        row(5, "echo", 0.5f, 400L),
        row(6, "foxtrot", 99f, 10L)
    )

    @Test
    fun ramSortKeepsHeaviestFirst() {
        assertEquals(listOf(3, 5, 1), selectTopRows(rows, WidgetSort.RAM, limit = 3).map { it.pid })
    }

    @Test
    fun cpuSortKeepsBusiestFirst() {
        assertEquals(listOf(6, 2, 4), selectTopRows(rows, WidgetSort.CPU, limit = 3).map { it.pid })
    }

    @Test
    fun nameSortIsCaseInsensitive() {
        // alpha, Bravo, charlie -- 'B' must not sort ahead of 'a'.
        assertEquals(listOf(1, 2, 3), selectTopRows(rows, WidgetSort.NAME, limit = 3).map { it.pid })
    }

    @Test
    fun truncatesToWidgetRowLimitByDefault() {
        assertEquals(MAX_WIDGET_ROWS, selectTopRows(rows, WidgetSort.RAM).size)
    }

    @Test
    fun emptyInputYieldsNoRows() {
        assertEquals(emptyList<WidgetProcRow>(), selectTopRows(emptyList(), WidgetSort.CPU))
    }

    @Test
    fun sortCyclesRamCpuName() {
        var sort = WidgetSort.RAM
        sort = sort.next()
        assertEquals(WidgetSort.CPU, sort)
        sort = sort.next()
        assertEquals(WidgetSort.NAME, sort)
        sort = sort.next()
        assertEquals(WidgetSort.RAM, sort)
    }

    @Test
    fun sortIdsMatchInAppSortbyValues() {
        // Ids line up with ProcessViewModel.Sortby so both surfaces share one preference domain.
        assertEquals(0, WidgetSort.RAM.id)
        assertEquals(1, WidgetSort.CPU.id)
        assertEquals(2, WidgetSort.NAME.id)
    }

    @Test
    fun unknownSortIdFallsBackToRam() {
        assertEquals(WidgetSort.RAM, WidgetSort.from(99))
        assertEquals(WidgetSort.CPU, WidgetSort.from(1))
    }

    @Test
    fun formatsKbAsCompactSizes() {
        assertEquals("1.0G", formatKb(1024L * 1024L))
        assertEquals("512M", formatKb(512L * 1024L))
        assertEquals("--", formatKb(0L))
    }
}