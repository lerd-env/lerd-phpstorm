package sh.lerd.ide.php

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.php.config.PhpLanguageLevel
import com.jetbrains.php.config.PhpProjectConfigurationFacade
import sh.lerd.ide.site.LerdState

/**
 * Keeps PhpStorm's PHP language level on the version lerd actually serves the
 * site with, so the IDE's own PHP widget and lerd's cannot disagree.
 *
 * One direction only. lerd owns what runs the site, and a language level
 * changed in the IDE is an inspection setting; making it rebuild a container
 * would be a surprise nobody asked for.
 */
object PhpLevelSync {
    fun apply(project: Project, state: LerdState) {
        if (!state.daemonReachable) return
        val wanted = PhpVersions.languageLevel(state.site?.phpVersion) ?: return

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            try {
                val facade = PhpProjectConfigurationFacade.getInstance(project)
                val level = PhpLanguageLevel.parse(wanted) ?: return@invokeLater
                if (facade.languageLevel == level) return@invokeLater
                ApplicationManager.getApplication().runWriteAction {
                    facade.languageLevel = level
                }
            } catch (e: Exception) {
                thisLogger().warn("could not set the PHP language level to $wanted", e)
            }
        }) { project.isDisposed }
    }
}
