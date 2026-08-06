package com.truevault.core.model

/**
 * How TrueVault presents itself in the launcher.
 *
 * What this can do: change the icon and the name on the home screen, and open into a real notes app
 * instead of a vault.
 *
 * What it **cannot** do, and the app says so on the screen where it is chosen:
 *
 *  - It does not hide the app from Android Settings, storage usage, battery usage, permission
 *    controls, or the app list a device owner can see.
 *  - It is not invisibility. Anyone who looks in Settings will find it.
 *
 * Every profile is a neutral identity belonging to this product. None of them imitates Android,
 * Google, a manufacturer, a bank, an antivirus, a system update or any other developer's app — that
 * would be impersonation, it would mislead the owner of the device, and it is exactly the kind of
 * thing that gets an app removed rather than merely rejected.
 */
enum class AppearanceProfile {

    /** The vault, under its own name. The default, and always available. */
    TRUE_VAULT,

    /** Neutral: opens to a working notes app, with the vault behind authentication. */
    NEXA,

    /** Same product, presented as notes. */
    NEXA_NOTES,

    /** Same product, presented as file organisation. */
    NEXA_FILES,
    ;

    companion object {
        val DEFAULT = TRUE_VAULT

        fun fromName(name: String?): AppearanceProfile =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The outcome of switching the launcher identity.
 *
 * A switch is two package-manager calls — enable the new alias, disable the old one — and a process
 * death between them would leave the app with no launcher entry at all, which the user could not
 * undo because they could not open the app. Every result below exists so that case is handled
 * rather than hoped away.
 */
sealed interface AppearanceSwitchResult {

    data class Applied(val profile: AppearanceProfile) : AppearanceSwitchResult

    /** Already the active profile. Toggling aliases again would only make the launcher flicker. */
    data object NoChange : AppearanceSwitchResult

    /**
     * The new alias could not be enabled. The previous one was left alone, so the app is still
     * launchable — the failure costs the user nothing but the change.
     */
    data class Failed(val safeReason: String) : AppearanceSwitchResult

    /**
     * The new alias is live but the old one could not be disabled, so two icons exist.
     *
     * Reported rather than hidden: two icons is confusing, and a user who is told why can fix it,
     * while a user who is not will assume the app is broken.
     */
    data class PartiallyApplied(val profile: AppearanceProfile) : AppearanceSwitchResult
}
