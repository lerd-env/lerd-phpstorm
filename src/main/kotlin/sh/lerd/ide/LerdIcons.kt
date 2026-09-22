package sh.lerd.ide

import com.intellij.openapi.util.IconLoader

/** Lerd's mark, as the IDE wants it: 13x13 for a tool window and its tabs. */
object LerdIcons {
    @JvmField
    val ToolWindow = IconLoader.getIcon("/icons/lerd.svg", LerdIcons::class.java)
}
