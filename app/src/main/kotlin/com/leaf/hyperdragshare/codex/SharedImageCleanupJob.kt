package com.leaf.hyperdragshare.codex

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Deletes published shared copies once the retention window has passed, without waiting for the
 * user to drag another image.
 *
 * [SharedImagePublisher.sweep] otherwise only runs from `stage_image`, so someone who shares one
 * image and then puts the phone down keeps a visible `Pictures` entry indefinitely — the ten
 * minutes would be a minimum age, not a maximum lifetime. A `Handler` cannot close that gap: the
 * module process is an ordinary background process with no foreground component and is killed
 * whenever the system wants the memory, while a scheduled job survives that and still runs.
 *
 * A single job id is reused, so a later publish simply re-arms the deadline for every outstanding
 * row instead of accumulating jobs.
 */
class SharedImageCleanupJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (params == null) {
            return false
        }
        val context = applicationContext
        Thread {
            var remaining = 0
            try {
                remaining = SharedImagePublisher.sweep(context)
            } catch (error: Throwable) {
                DragShareLog.w(TAG, "scheduled sweep failed", error)
            }
            if (remaining > 0) {
                // Rows that are still too young to drop: give them their own deadline.
                schedule(context)
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    /** Nothing is left half-done, and the reschedule keeps the deadline alive. */
    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val TAG = "DragShareProvider"
        private const val JOB_ID = 0x64726167

        /** Runs a little after the cutoff so the newest row is strictly older than it. */
        private const val MARGIN_MS = 30L * 1000L

        /** Upper bound, so battery heuristics can delay the sweep but not cancel it. */
        private const val SLACK_MS = 5L * 60L * 1000L

        /** Arms (or re-arms) the deadline for every shared copy currently published. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                as? JobScheduler ?: return
            val component = ComponentName(context, SharedImageCleanupJob::class.java)
            val job = JobInfo.Builder(JOB_ID, component)
                .setMinimumLatency(SharedImagePublisher.RETENTION_MS + MARGIN_MS)
                .setOverrideDeadline(SharedImagePublisher.RETENTION_MS + SLACK_MS)
                .build()
            try {
                scheduler.schedule(job)
            } catch (error: Throwable) {
                DragShareLog.w(TAG, "cleanup schedule failed", error)
            }
        }
    }
}
