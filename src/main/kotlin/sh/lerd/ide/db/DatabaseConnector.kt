package sh.lerd.ide.db

import com.intellij.credentialStore.OneTimeString
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.DasUtil
import com.intellij.database.view.DatabaseView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm

/**
 * Opens a lerd service in the IDE's Database tool window.
 *
 * An existing connection to the same server wins, which is how lerd's own
 * entry in .idea/dataSources.xml gets reused rather than duplicated. A data
 * source this creates is deliberately not named with lerd's " (lerd)" suffix
 * convention alone, because lerd prunes suffixed entries it does not own.
 */
object DatabaseConnector {
    fun open(project: Project, target: DataSourceTarget) {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            try {
                val source = existing(project, target) ?: create(project, target)
                storePassword(source, target)
                val dataSource = DbPsiFacade.getInstance(project).findDataSource(source.uniqueId)
                    ?: return@invokeLater
                DatabaseView.select(project, listOf(databaseNode(dataSource, target) ?: dataSource), true)
                // A data source that has never connected knows no databases
                // yet, so introspect and land on the right one once it does.
                introspect(project)
                selectWhenLoaded(project, source, target)
            } catch (e: Exception) {
                thisLogger().warn("could not open ${target.name} in the database tools", e)
            }
        }) { project.isDisposed }
    }

    /**
     * Land on the site's database rather than on the server node. Right after a
     * data source is created nothing is introspected yet, so the server node is
     * the honest answer until it is.
     */
    private fun databaseNode(dataSource: DbDataSource, target: DataSourceTarget): DbElement? {
        val name = target.database ?: return null
        val schema = DasUtil.getSchemas(dataSource).find { it.name.equals(name, ignoreCase = true) }
            ?: return null
        return dataSource.findElement(schema)
    }

    /**
     * Introspection is what fills the tree under a data source. The utility
     * class that used to start it was removed in 2026.2, so go through the
     * registered action instead: action ids outlive their implementations.
     */
    private fun introspect(project: Project) {
        val action = ActionManager.getInstance().getAction(REFRESH_ACTION) ?: return
        runCatching { ActionUtil.invokeAction(action, DataContext.EMPTY_CONTEXT, ActionPlaces.UNKNOWN, null, null) }
            .onFailure { thisLogger().warn("could not start introspection", it) }
    }

    /** Polls briefly rather than hooking the loader, which is internal API. */
    private fun selectWhenLoaded(project: Project, source: LocalDataSource, target: DataSourceTarget) {
        if (target.database == null) return
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
        var attempts = 0
        lateinit var poll: () -> Unit
        poll = {
            attempts++
            val dataSource = DbPsiFacade.getInstance(project).findDataSource(source.uniqueId)
            val node = dataSource?.let { databaseNode(it, target) }
            when {
                project.isDisposed -> Unit
                node != null -> DatabaseView.select(project, listOf(node), true)
                attempts < POLL_ATTEMPTS -> alarm.addRequest({ poll() }, POLL_INTERVAL_MS)
                else -> Unit
            }
        }
        alarm.addRequest({ poll() }, POLL_INTERVAL_MS)
    }

    fun isAvailable(): Boolean = runCatching { DatabaseDriverManager.getInstance() }.isSuccess

    private fun existing(project: Project, target: DataSourceTarget): LocalDataSource? =
        LocalDataSourceManager.getInstance(project).dataSources
            .firstOrNull { it.url?.let { url -> DataSourceTargets.sameServer(url, target.url) } == true }

    /**
     * The builder does not put the password in the credential store, and the
     * entry lerd writes into .idea carries no password either, so without this
     * every connection stops to ask for one lerd already knows.
     */
    private fun storePassword(source: LocalDataSource, target: DataSourceTarget) {
        val password = target.password ?: return
        source.passwordStorage = LocalDataSource.Storage.PERSIST
        DatabaseCredentials.getInstance().storePassword(source, OneTimeString(password.toCharArray()))
    }

    private fun create(project: Project, target: DataSourceTarget): LocalDataSource {
        val registry = DataSourceRegistry(project)
        registry.setImportedFlag(false)

        // Pick an id the running IDE actually ships a driver for; a data source
        // with no driver opens to an error dialog rather than to the database.
        val driverId = target.driverCandidates
            .firstOrNull { DatabaseDriverManager.getInstance().getDriver(it) != null }
            ?: error("no driver for ${target.driverCandidates.joinToString()}")

        registry.builder
            .withName(target.name)
            .withUrl(target.url)
            .apply {
                withDriver(driverId)
                target.user?.let { withUser(it) }
                target.password?.let { withPassword(it) }
            }
            .commit()

        val created = registry.dataSources.last()
        LocalDataSourceManager.getInstance(project).addDataSource(created)
        return created
    }

    private const val REFRESH_ACTION = "DatabaseView.Refresh"
    private const val POLL_INTERVAL_MS = 700
    private const val POLL_ATTEMPTS = 20
}
