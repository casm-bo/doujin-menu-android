package com.doujinmenu.android.data

import android.content.Context
import androidx.work.*
import com.doujinmenu.android.model.DesktopProfile
import com.doujinmenu.android.security.SecureProfileStore
import com.doujinmenu.android.security.SubscriptionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SubscriptionRefresh {
    private val mutex=Mutex()
    suspend fun run(context:Context,profile:DesktopProfile?,force:Boolean=false,forceSync:Boolean=false):String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val store=SubscriptionStore.get(context)
            val scope=profile?.id.orEmpty()
            store.adoptLocal(scope)
            val options=SubscriptionPreferences(context).load()
            val now=System.currentTimeMillis()
            if(profile != null && com.doujinmenu.android.security.subscriptionSyncDue(options,now,
                store.state("sync-attempt/$scope")?.toLongOrNull(),store.state("sync-error/$scope")=="unsupported",forceSync)) {
                store.setState("sync-attempt/$scope",now.toString())
                try { SubscriptionSync(store).sync(profile);store.setState("sync-error/$scope","") }
                catch(error:CancellationException) { throw error }
                catch(error:Exception) { store.setState("sync-error/$scope",if(error is com.doujinmenu.android.network.CompanionApiException && error.statusCode==404) "unsupported" else "offline") }
            }
            SubscriptionChecker(store).check(scope,options.intervalMinutes*60_000L,force)
            SubscriptionNotifications.refresh(context)
            when(store.state("sync-error/$scope")) {
                "unsupported" -> context.getString(com.doujinmenu.android.R.string.sub_update_pc)
                "offline" -> context.getString(com.doujinmenu.android.R.string.sub_sync_offline)
                else -> null
            }
        }
    }
}

class SubscriptionWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result = try {
        val profiles=withContext(Dispatchers.IO) { SecureProfileStore(applicationContext).load() }
        if(profiles.isEmpty()) SubscriptionRefresh.run(applicationContext,null)
        else for(profile in profiles) SubscriptionRefresh.run(applicationContext,profile)
        Result.success()
    } catch(error:CancellationException) { throw error }
    catch(_:Exception) { Result.retry() }

    companion object {
        const val WORK_NAME="subscription-checks"
        fun schedule(context:Context):Operation {
            val store=SubscriptionStore.get(context)
            val manager=WorkManager.getInstance(context)
            val profiles=SecureProfileStore(context).load()
            val options=SubscriptionPreferences(context).load()
            val hasChecks=(listOf("")+profiles.map { it.id }).any { scope -> store.records(scope).any { it.value.enabled && !it.value.deleted } }
            if(!hasChecks && (profiles.isEmpty() || !options.syncEnabled)) {
                return manager.cancelUniqueWork(WORK_NAME)
            }
            val minutes=if(options.syncEnabled && profiles.isNotEmpty()) minOf(options.intervalMinutes,options.syncIntervalMinutes) else options.intervalMinutes
            val request=PeriodicWorkRequestBuilder<SubscriptionWorker>(minutes.toLong(),TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            return manager.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
        }
    }
}
