package com.safenest.app

import android.app.Application
import android.util.Log

class SafeNestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProtectionCommitment.checkpoint(applicationContext)
        // RulesStore dispatches outside its lock, on its own worker. No browser
        // policy or network operation runs on the DNS packet-reader thread.
        RulesStore.addChangeListener { context ->
            try { ManagedProtection.refreshDomainPolicy(context) }
            catch (error: Exception) { Log.w("SafeNestPolicy", "Browser policy refresh failed: ${error.javaClass.simpleName}") }
        }
        Thread({
            try {
                ProtectionCommitment.releaseExpired(applicationContext)
                CatalogStore.status(applicationContext)
                CatalogUpdateJob.schedule(applicationContext)
                ManagedProtection.refreshDomainPolicy(applicationContext)
            } catch (error: Exception) {
                Log.w("SafeNestPolicy", "Stored policy load failed: ${error.javaClass.simpleName}")
            }
        }, "SafeNest-policy-startup").start()
    }
}
