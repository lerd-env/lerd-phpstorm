package sh.lerd.ide.php

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import sh.lerd.ide.site.LerdState
import com.jetbrains.php.config.PhpProjectWorkspaceConfiguration
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl
import java.nio.file.Files
import java.nio.file.Path

/**
 * Points PhpStorm's CLI interpreter at lerd's php shim.
 *
 * The shim is the only php on the host that runs a site's real PHP, with its
 * extensions and its version, and it already rewrites the paths in its output
 * for exactly this case. Without it the IDE runs whatever php happens to be on
 * PATH, which for lerd is usually none at all.
 */
object PhpInterpreterSync {
    private const val NAME = "Lerd"

    fun apply(project: Project, state: LerdState) {
        // Never touch a project lerd does not own; its interpreter is not ours.
        if (state.site == null) return
        val shim = shimPath() ?: return

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            try {
                val manager = PhpInterpretersManagerImpl.getInstance(project)
                val existing = manager.findInterpreter(NAME)
                val interpreter = existing ?: PhpInterpreter().apply {
                    name = NAME
                    setIsProjectLevel(false)
                }
                if (interpreter.homePath != shim.toString()) {
                    interpreter.homePath = shim.toString()
                }
                if (existing == null) {
                    manager.addInterpreter(interpreter)
                }

                val workspace = project.getService(PhpProjectWorkspaceConfiguration::class.java)
                val state = workspace?.state ?: return@invokeLater
                if (state.interpreterName != NAME) {
                    state.interpreterName = NAME
                }
            } catch (e: Exception) {
                thisLogger().warn("could not point the PHP interpreter at lerd's shim", e)
            }
        }) { project.isDisposed }
    }

    private fun shimPath(): Path? {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        val base = xdg?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".local", "share")
        return base.resolve("lerd").resolve("bin").resolve("php").takeIf { Files.isExecutable(it) }
    }
}
