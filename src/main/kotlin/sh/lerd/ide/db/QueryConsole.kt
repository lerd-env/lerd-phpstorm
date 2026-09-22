package sh.lerd.ide.db

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Opens a finding's SQL in a scratch file the IDE runs as SQL, so Ctrl+Enter
 * executes it against the data source the editor is attached to.
 *
 * The data source is opened first, which both connects it and makes it the one
 * the editor offers, so the query is one keystroke from running.
 */
object QueryConsole {
    fun open(project: Project, target: DataSourceTarget?, kind: String, request: String, sql: String) {
        target?.let { DatabaseConnector.open(project, it) }

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            try {
                val file = ScratchRootType.getInstance().createScratchFile(
                    project,
                    QueryScratch.fileName(target?.name.orEmpty()),
                    com.intellij.lang.Language.findLanguageByID("SQL")
                        ?: com.intellij.openapi.fileTypes.PlainTextLanguage.INSTANCE,
                    QueryScratch.text(kind, request, sql),
                    ScratchFileService.Option.create_new_always,
                ) ?: return@invokeLater
                FileEditorManager.getInstance(project).openFile(file, true)
            } catch (e: Exception) {
                thisLogger().warn("could not open the query in an editor", e)
            }
        }) { project.isDisposed }
    }
}
