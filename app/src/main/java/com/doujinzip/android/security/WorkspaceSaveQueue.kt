package com.doujinzip.android.security

import com.doujinzip.android.model.BrowserWorkspace
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** A single IO consumer preserves submission order across both workspaces. */
internal class WorkspaceSaveQueue(private val save: (Boolean, BrowserWorkspace) -> Unit) {
    private sealed interface Request {
        data class Save(val gallery: Boolean, val workspace: BrowserWorkspace) : Request
        data class Flush(val completed: CompletableDeferred<Unit>) : Request
    }
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val worker = CoroutineScope(Dispatchers.IO).launch {
        val failures = mutableMapOf<Boolean, Exception>()
        for (request in requests) when (request) {
            is Request.Save -> try {
                save(request.gallery, request.workspace)
                failures.remove(request.gallery)
            } catch (failure: Exception) { failures[request.gallery] = failure }
            is Request.Flush -> failures.values.firstOrNull()?.let { request.completed.completeExceptionally(it) }
                ?: run { request.completed.complete(Unit) }
        }
    }
    fun submit(gallery: Boolean, workspace: BrowserWorkspace) { check(requests.trySend(Request.Save(gallery, workspace)).isSuccess) }
    suspend fun flush() {
        val completed = CompletableDeferred<Unit>()
        requests.send(Request.Flush(completed)); completed.await()
    }
    suspend fun close() { requests.close(); worker.join() }
}
