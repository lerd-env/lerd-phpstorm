package sh.lerd.ide.services

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/** Opens a terminal tab in the IDE and runs one command in it. */
object TerminalLauncher {
    fun run(project: Project, title: String, command: ClientCommand, workingDirectory: String?) {
        val binary = ServiceClients.resolveBinary(command.binary)
        run(project, title, listOf(binary) + command.args, workingDirectory)
    }

    fun run(project: Project, title: String, command: List<String>, workingDirectory: String?) {
        TerminalToolWindowManager.getInstance(project)
            .createShellWidget(workingDirectory, title, true, true)
            .sendCommandToExecute(command.joinToString(" ") { quote(it) })
    }

    private fun quote(arg: String): String =
        if (arg.none { it.isWhitespace() || it in "\"'$`\\" }) arg else "'" + arg.replace("'", "'\\''") + "'"
}
