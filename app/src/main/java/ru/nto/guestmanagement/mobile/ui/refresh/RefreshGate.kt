package ru.nto.guestmanagement.mobile.ui.refresh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RefreshGate {
    private var current: Job? = null

    fun launch(scope: CoroutineScope, block: suspend () -> Unit): Job? {
        val active = current
        if (active != null && active.isActive) return null
        val job = scope.launch { block() }
        current = job
        return job
    }

    fun cancel() {
        current?.cancel()
        current = null
    }
}
