package com.bizzeh.bruce

import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsAppNameAndPlaceholder() {
        composeRule.onNodeWithText("Bruce").assertIsDisplayed()
        composeRule.onNodeWithText("Bruce is getting ready.").assertIsDisplayed()
    }

    @Test
    fun appDataIsExcludedFromBackup() {
        val flags = composeRule.activity.applicationInfo.flags
        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
