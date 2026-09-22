package sh.lerd.ide.toolwindow

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Lays its sections out side by side, dropping to fewer columns as it narrows
 * and back again as it widens. Each section keeps its natural height and sits
 * at the top of its cell, so a short one does not stretch to match a tall one.
 */
class ResponsiveColumns(sections: List<JComponent>) : JPanel(GridLayout(1, 1)) {
    private val cells = sections.map { section ->
        JPanel(BorderLayout()).apply {
            isOpaque = false
            add(section, BorderLayout.NORTH)
        }
    }

    private var columns = 0

    init {
        isOpaque = false
        cells.forEach { add(it) }
        relayout()
    }

    override fun doLayout() {
        relayout()
        super.doLayout()
    }

    override fun getPreferredSize(): Dimension {
        val rows = ColumnFit.rows(cells.size, columns.coerceAtLeast(1))
        val height = cells.chunked(columns.coerceAtLeast(1))
            .sumOf { row -> row.maxOfOrNull { it.preferredSize.height } ?: 0 }
        val width = cells.maxOfOrNull { it.preferredSize.width } ?: 0
        return Dimension(width * columns.coerceAtLeast(1), maxOf(height, rows))
    }

    private fun relayout() {
        val next = ColumnFit.columns(width, cells.size, MIN_COLUMN_WIDTH)
        if (next == columns) return
        columns = next
        // GridLayout derives the column count from rows when both are set, so
        // rows is the one that has to be right.
        (layout as GridLayout).rows = ColumnFit.rows(cells.size, next)
        // The height changes with the column count, so the scroll pane has to
        // be told; the guard above keeps this from looping.
        parent?.revalidate()
    }

    private companion object {
        val MIN_COLUMN_WIDTH = JBUI.scale(360)
    }
}
