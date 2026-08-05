package com.truevault.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Motion tokens.
 *
 * Animation here exists to explain a state change — a score filling in, a sheet arriving, a status
 * pill switching colour. Nothing loops, nothing bounces for decoration.
 *
 * Every duration passes through [TvMotion.duration], which returns 0 when the user has turned
 * animations off in system settings. That makes reduced-motion support a property of the design
 * system rather than something each screen has to remember.
 */
object TvMotion {

    /** Standard Material easing: fast out, gentle in. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For elements entering the screen. */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For elements leaving. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val Linear: Easing = LinearEasing

    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 250
    const val DURATION_LONG = 400

    /** Value animations, e.g. the privacy score counting up. */
    const val DURATION_VALUE = 700

    @Composable
    @ReadOnlyComposable
    fun duration(base: Int): Int = if (LocalReducedMotion.current) 0 else base

    @Composable
    fun <T> enterSpec(durationMillis: Int = DURATION_MEDIUM): FiniteAnimationSpec<T> =
        tween(durationMillis = duration(durationMillis), easing = Emphasized)

    @Composable
    fun <T> exitSpec(durationMillis: Int = DURATION_SHORT): FiniteAnimationSpec<T> =
        tween(durationMillis = duration(durationMillis), easing = Accelerate)

    @Composable
    fun <T> standardSpec(durationMillis: Int = DURATION_MEDIUM): FiniteAnimationSpec<T> =
        tween(durationMillis = duration(durationMillis), easing = Standard)

    /** Spring used for size and offset changes; collapses to a snap under reduced motion. */
    @Composable
    fun <T> springSpec(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) {
        tween(durationMillis = 0)
    } else {
        spring(dampingRatio = 0.85f, stiffness = 380f)
    }
}

/** True when the user has disabled or heavily reduced animations at the system level. */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        if (inspecting) {
            false
        } else {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)
        }
    }
}
