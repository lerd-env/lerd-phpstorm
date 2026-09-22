package sh.lerd.ide.site

import sh.lerd.ide.api.LerdSite

/**
 * One worker a site can run, in the two shapes lerd has: the named ones the
 * daemon has dedicated actions and endpoints for, and the generic ones a
 * framework definition declares.
 */
data class WorkerControl(
    val name: String,
    val label: String,
    val running: Boolean,
    val failing: Boolean = false,
    val unreachable: Boolean = false,
    val named: Boolean = false,
) {
    val startAction: String get() = action("start")
    val stopAction: String get() = action("stop")

    private fun action(verb: String): String =
        if (named) "$name:$verb" else "worker:$name:$verb"

    /**
     * unitName differs from siteName only for a worktree, whose generic worker
     * units are suffixed with the worktree directory.
     */
    fun logPath(siteName: String, unitName: String): String =
        if (named) "/api/$name/$siteName/logs" else "/api/worker/$unitName/$name/logs"
}

/**
 * The workers a site has, declared or merely running. A worker that is up but
 * undeclared still gets a control, because something has to be able to stop it.
 */
object SiteWorkers {
    fun of(site: ResolvedSite): List<WorkerControl> {
        val s = site.site
        val controls = mutableListOf<WorkerControl>()

        fun named(name: String, label: String, present: Boolean, running: Boolean, failing: Boolean) {
            if (present || running || failing) {
                controls += WorkerControl(name, label, running, failing, named = true)
            }
        }

        named("queue", "Queue", s.hasQueueWorker, s.queueRunning, s.queueFailing)
        named("horizon", "Horizon", s.hasHorizon, s.horizonRunning, s.horizonFailing)
        named("schedule", "Scheduler", s.hasScheduleWorker, s.scheduleRunning, s.scheduleFailing)
        named("reverb", "Reverb", s.hasReverb, s.reverbRunning, s.reverbFailing)
        named("stripe", "Stripe", s.stripeSecretSet, s.stripeRunning, false)

        site.workers.forEach { worker ->
            controls += WorkerControl(
                name = worker.name,
                label = worker.label ?: worker.name,
                running = worker.running,
                failing = worker.failing,
                unreachable = worker.unreachable,
            )
        }
        return controls
    }
}
