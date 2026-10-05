package com.safenest.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Periodic signed catalogue refresh only; never uploads browsing activity. */
class CatalogUpdateJob : JobService() {
    private val generation = AtomicInteger(0)
    private val worker = Executors.newSingleThreadExecutor()
    override fun onStartJob(params: JobParameters): Boolean {
        val run = generation.incrementAndGet()
        worker.execute {
            val status = CatalogStore.refresh(applicationContext)
            if (generation.get() == run) jobFinished(params, status.lastError != null)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { generation.incrementAndGet(); return true }
    override fun onDestroy() { generation.incrementAndGet(); worker.shutdown(); super.onDestroy() }
    companion object {
        private const val ID = 7304
        fun schedule(context: Context) {
            val configuration = CatalogStore.configuration(context)
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (configuration.sourceUrl.isBlank() || configuration.publicKeyPem.isBlank()) {
                scheduler.cancel(ID)
                return
            }
            scheduler.schedule(JobInfo.Builder(ID, ComponentName(context, CatalogUpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L)
                .setPersisted(true)
                .setBackoffCriteria(30 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
        }
    }
}
