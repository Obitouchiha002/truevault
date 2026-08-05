package com.truevault.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-run flow.
 *
 * The assertion that matters most here is the last one: the vault cannot be entered without
 * creating a lock. A regression that let a user reach Home straight from onboarding would defeat
 * the entire app, and it is exactly the kind of thing a navigation refactor breaks silently.
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
        composeRule.waitForIdle()

        // Skipping the introduction leads to lock creation, never straight into the vault.
        composeRule.onNodeWithText("Create your vault lock").assertIsDisplayed()
    }

    @Test
    fun theUnrecoverableWarningIsShownBeforeAPasswordIsChosen() {
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("There is no way to reset this").assertIsDisplayed()
    }
}
