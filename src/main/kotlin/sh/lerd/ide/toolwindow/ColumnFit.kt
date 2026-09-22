package sh.lerd.ide.toolwindow

/**
 * How many columns the site panel's sections get at a given width. The tool
 * window is as often a narrow strip at the side as a wide band at the bottom,
 * and a fixed column count is wrong in one of the two.
 */
object ColumnFit {
    fun columns(width: Int, sections: Int, minColumnWidth: Int): Int =
        (width / minColumnWidth).coerceIn(1, sections.coerceAtLeast(1))

    fun rows(sections: Int, columns: Int): Int =
        if (sections <= 0) 1 else (sections + columns - 1) / columns
}
