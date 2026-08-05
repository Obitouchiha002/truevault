package com.truevault.core.common.dispatcher

import javax.inject.Qualifier

/**
 * Dispatchers are injected, never referenced statically, so tests can substitute a test scheduler
 * and so no layer is tempted to do file or crypto work on the main thread.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: TrueVaultDispatcher)

enum class TrueVaultDispatcher {
    /** General background work: database, parsing, small computations. */
    IO,

    /** CPU-bound work: hashing, key derivation, thumbnail decoding. */
    Default,

    /** UI thread. Injected only where a platform API demands it. */
    Main,
}

/** Marks the application-scoped [kotlinx.coroutines.CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
