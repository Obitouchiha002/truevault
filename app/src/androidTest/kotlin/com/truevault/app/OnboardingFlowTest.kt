package com.truevault.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The emulator is slow enough that a screen transition can outlast `waitForIdle`. */
private const val TRANSITION_TIMEOUT_MS = 10_000L

/**
 * First-run flow.
 *
 * The assertion that matters most here is that the vault cannot be entered without creating a lock.
 * A regression that let a user reach Home straight from onboarding would defeat the entire app, and
 * it is exactly the kind of thing a navigation refactor breaks silently.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingShowsItsFirstPageAndCanBeSkipped() {
        composeRule.onNodeWithText("Protect your private files").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").performClick()

        // Skipping the introduction leads to lock creation, never straight into the vault. The
        // assertion is on the first thing that screen asks, not on the app-bar title: the title
        // moved once already, and a test that pins the wrong string fails for a reason that has
        // nothing to do with the behaviour it exists to protect.
        awaitText("How do you want to lock your vault?")
        composeRule.onNodeWithText("How do you want to lock your vault?").assertIsDisplayed()
    }

    @Test
    fun theUnrecoverableWarningIsShownBeforeAPasswordIsChosen() {
        composeRule.onNodeWithText("Skip").performClick()

        // The warning must be on the screen where the lock method is chosen, not on the one after
        // it: a 4-digit PIN is a decision the user cannot walk back later.
        awaitText("There is no way to reset this")
        composeRule.onNodeWithText("There is no way to reset this").assertIsDisplayed()
    }

    /**
     * Waits for a node to exist before asserting on it.
     *
     * `waitForIdle` alone proved flaky here: the same assertion passed in one run and failed in the
     * next, because the navigation transition had not settled when the assertion ran. This waits for
     * the node to appear and then still asserts it is *displayed* — the assertion is unchanged, only
     * the race is removed. Weakening it to `assertExists` would have made the test pass while
     * proving nothing about whether the user can actually see the warning.
     */
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = TRANSITION_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
