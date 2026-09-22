package sh.lerd.ide.toolwindow

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager

/**
 * A console tails, so it sticks to the end of what it prints. A detail pane is
 * not a tail: the line that matters is the first one, and scrolling past it to
 * the bottom of a thirty frame stack is the opposite of useful.
 *
 * The scroll is scheduled rather than immediate because the console flushes
 * what it was given asynchronously, and scrolling before the flush lands is
 * undone by it.
 */
fun ConsoleView.showFromTop() {
    ApplicationManager.getApplication().invokeLater { scrollTo(0) }
}
