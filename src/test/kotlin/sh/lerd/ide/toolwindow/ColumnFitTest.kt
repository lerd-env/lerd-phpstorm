package sh.lerd.ide.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnFitTest {
    @Test
    fun `a wide panel takes every column`() {
        assertEquals(3, ColumnFit.columns(width = 1200, sections = 3, minColumnWidth = 300))
    }

    @Test
    fun `a narrower panel drops to what fits`() {
        assertEquals(2, ColumnFit.columns(width = 700, sections = 3, minColumnWidth = 300))
    }

    @Test
    fun `a docked strip stays in one column`() {
        assertEquals(1, ColumnFit.columns(width = 320, sections = 3, minColumnWidth = 300))
    }

    @Test
    fun `never more columns than there are sections`() {
        assertEquals(2, ColumnFit.columns(width = 4000, sections = 2, minColumnWidth = 300))
    }

    @Test
    fun `a panel with no width yet still lays out`() {
        // Swing asks for a layout before the component has been sized.
        assertEquals(1, ColumnFit.columns(width = 0, sections = 3, minColumnWidth = 300))
    }

    @Test
    fun `rows follow from the columns`() {
        assertEquals(1, ColumnFit.rows(sections = 3, columns = 3))
        assertEquals(2, ColumnFit.rows(sections = 3, columns = 2))
        assertEquals(3, ColumnFit.rows(sections = 3, columns = 1))
        assertEquals(1, ColumnFit.rows(sections = 0, columns = 1))
    }
}
