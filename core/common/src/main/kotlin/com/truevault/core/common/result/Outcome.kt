package com.truevault.core.common.result

import com.truevault.core.model.VaultError

/**
 * The single result type used across repository and use-case boundaries.
 *
 * Kotlin's own `Result` is deliberately not used: it carries a `Throwable`, and throwables in this
 * app routinely contain file names, URIs and provider paths that must never travel up to the UI.
 */
sealed interface Outcome<out T> {

    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: VaultError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    val isFailure: Boolean get() = this is Failure
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Success) action(value)
}

inline fun <T> Outcome<T>.onFailure(action: (VaultError) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Failure) action(error)
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): VaultError? = (this as? Outcome.Failure)?.error

fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)

fun VaultError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)
