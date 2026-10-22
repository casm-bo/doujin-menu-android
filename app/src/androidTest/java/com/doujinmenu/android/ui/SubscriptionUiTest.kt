package com.doujinmenu.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.model.SearchSubscription
import com.doujinmenu.android.data.SubscriptionNotice
import com.doujinmenu.android.security.SubscriptionOptions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SubscriptionUiTest {
    @get:Rule val compose=createComposeRule()
    private fun text(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Test fun noticeOpeningAndMarkAllAreExplicitAndScreenEntryDoesNotRead() {
        val reads=mutableListOf<Long?>()
        compose.setContent { MaterialTheme { SubscriptionScreen(MainUiState(subscriptionNotices=listOf(SubscriptionNotice(42,"Synthetic release","Example",null,false,listOf("Example subscription")))),{}, {}, {}, {reads+=it}, {}, {}, {_,_->}, {}) } }
        compose.onNodeWithText("Synthetic release").assertIsDisplayed()
        assertTrue(reads.isEmpty())
        compose.onNodeWithText("Synthetic release").performClick()
        assertEquals(listOf(42L),reads)
        compose.onNodeWithText(text(R.string.sub_all_read)).performClick()
        assertEquals(listOf(42L,null),reads)
    }
    @Test fun keywordShortcutCannotEnableMonitoringButCanBeSavedInactive() {
        var saved:SearchSubscription?=null
        compose.setContent { MaterialTheme { SubscriptionScreen(MainUiState(subscriptionTab=1,subscriptionEditor=SearchSubscription(name="Example",query="title words",enabled=true)),{}, {}, {}, {}, {}, {saved=it}, {_,_->}, {}) } }
        compose.onNodeWithText(text(R.string.ui_save)).performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("subscription-editor-monitor").performClick()
        compose.onNodeWithText(text(R.string.ui_save)).performClick()
        compose.runOnIdle { assertEquals("title words",saved!!.query);assertFalse(saved!!.enabled) }
    }
    @Test fun privacyStartsEnabledAndCanBeDisabledAndIntervalCanChange() {
        var options=SubscriptionOptions()
        compose.setContent { var state by remember { mutableStateOf(options) }; MaterialTheme { SubscriptionSettings(state,{state=it;options=it},{}) } }
        compose.onNodeWithTag("subscription-hide-locked").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertFalse(options.hideWhenLocked) }
        val minutes=InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.sub_minutes,180)
        compose.onNodeWithText(minutes).performClick()
        compose.runOnIdle { assertEquals(180,options.intervalMinutes) }
    }
}
