package com.truevault.core.model

/** Theme selection. Dynamic colour is a separate opt-in so the branded palette stays the default. */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * How long an authenticated session survives once TrueVault is no longer in the foreground.
 *
 * [IMMEDIATE] is the default: leaving the app locks the vault.
 */
enum class AutoLockDuration(val millis: Long) {
    IMMEDIATE(0L),
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    FIFTEEN_MINUTES(15 * 60_000L),
}

/** Ordering options for the vault list. */
enum class VaultSortOrder {
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    TYPE,
}

/** Vault list presentation. */
enum class VaultLayout {
    GRID,
    LIST,
}
