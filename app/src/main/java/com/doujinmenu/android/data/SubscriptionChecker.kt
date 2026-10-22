package com.doujinmenu.android.data

import com.doujinmenu.android.model.subscriptionNewIds
import com.doujinmenu.android.network.HitomiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubscriptionChecker(private val store: SubscriptionStore, private val client: HitomiClient = HitomiClient()) {
    suspend fun check(scope: String, intervalMillis: Long, force: Boolean = false): Unit = mutex.withLock {
        for (record in store.records(scope).filter { it.value.enabled && !it.value.deleted }) {
            val now = System.currentTimeMillis()
            if (!force && now - record.checkedAt < intervalMillis) continue
            try {
                val ids = record.value.queries.flatMap { client.subscriptionIds(it) }.distinct()
                val galleries = subscriptionNewIds(ids, record.baseline).map { client.getGallery(it) }
                store.checked(scope, record.value, ids, galleries, now)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { store.failed(scope, record.value.id, error.message ?: "신작 확인 실패") }
        }
    }
    companion object { private val mutex = Mutex() }
}
