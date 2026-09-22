package sh.lerd.ide.logs

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/** Turns the file references in a log line into links into the editor. */
class LerdLogFilter(
    private val project: Project,
    private val siteRoot: () -> String?,
) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val hits = FileLineMatcher.find(line)
        if (hits.isEmpty()) return null

        val lineStart = entireLength - line.length
        val items = hits.mapNotNull { hit ->
            val file = resolve(hit.path) ?: return@mapNotNull null
            Filter.ResultItem(
                lineStart + hit.start,
                lineStart + hit.end,
                OpenFileHyperlinkInfo(project, file, hit.line - 1),
            )
        }
        return items.takeIf { it.isNotEmpty() }?.let { Filter.Result(it) }
    }

    private fun resolve(path: String): VirtualFile? =
        LogPaths.candidates(path, siteRoot())
            .firstNotNullOfOrNull { LocalFileSystem.getInstance().findFileByPath(it) }
}
