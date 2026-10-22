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
    suspend fun run(context:Context,profile:DesktopProfile?,force:Boolean=false):String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val store=SubscriptionStore.get(context)
            val scope=profile?.id.orEmpty()
            store.adoptLocal(scope)
            var syncError:String?=null
            if(profile != null) try { SubscriptionSync(store).sync(profile) }
            catch(error:CancellationException) { throw error }
            catch(error:Exception) { syncError=if(error is com.doujinmenu.android.network.CompanionApiException && error.statusCode==404)
                context.getString(com.doujinmenu.android.R.string.sub_update_pc) else context.getString(com.doujinmenu.android.R.string.sub_sync_offline) }
            SubscriptionChecker(store).check(scope,SubscriptionPreferences(context).load().intervalMinutes*60_000L,force)
            SubscriptionNotifications.refresh(context)
            syncError
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
            if(SecureProfileStore(context).load().isEmpty() && store.records("").none { it.value.enabled && !it.value.deleted }) {
                return manager.cancelUniqueWork(WORK_NAME)
            }
            val request=PeriodicWorkRequestBuilder<SubscriptionWorker>(SubscriptionPreferences(context).load().intervalMinutes.toLong(),TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            return manager.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
        }
    }
}
