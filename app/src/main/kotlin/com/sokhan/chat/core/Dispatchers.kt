package com.sokhan.chat.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Injected dispatchers keep logic testable without touching global state.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
}

/**
 * Application-scoped coroutine scope for background jobs that outlive
 * ViewModels (e.g. streams started from the chat screen and intentionally
 * left running when the user navigates away).
 */
fun behindAppScope(dispatchers: DispatcherProvider): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatchers.default)
