package sh.lerd.ide.php

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.php.config.servers.PhpServer
import com.jetbrains.php.config.servers.PhpServersWorkspaceStateComponent
import sh.lerd.ide.site.LerdState

/**
 * Registers the site as a PHP server, which is what Xdebug needs to turn an
 * incoming connection into breakpoints in this project rather than a prompt.
 *
 * No path mappings: lerd bind-mounts the home directory at the same absolute
 * path inside its containers, so the paths the debugger reports are already
 * the paths on disk.
 */
object PhpServerSync {
    private const val XDEBUG = "xdebug"

    fun apply(project: Project, state: LerdState) {
        val site = state.site ?: return
        val domain = site.domain.takeIf { it.isNotBlank() } ?: return
        val port = if (site.site.tls) 443 else 80

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            try {
                val component = PhpServersWorkspaceStateComponent.getInstance(project)
                val servers = component.servers.orEmpty().toMutableList()
                val existing = servers.find { it.name == domain }

                if (existing != null) {
                    if (existing.host == domain && existing.port == port) return@invokeLater
                    existing.setHost(domain)
                    existing.setPort(port)
                    existing.setDebuggerId(XDEBUG)
                } else {
                    servers.add(PhpServer(domain, domain, port, XDEBUG, false))
                }
                component.servers = servers
            } catch (e: Exception) {
                thisLogger().warn("could not register $domain as a PHP server", e)
            }
        }) { project.isDisposed }
    }
}
