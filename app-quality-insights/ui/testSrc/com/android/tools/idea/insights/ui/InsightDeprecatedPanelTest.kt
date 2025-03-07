/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.ServiceDeprecationInfo.Panel.INSIGHTS_PANEL
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.HyperlinkLabel
import javax.swing.JButton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class InsightDeprecatedPanelTest {

  @get:Rule val projectRule = ProjectRule()

  private val tracker = mock<AppInsightsTracker>()
  private val scope = CoroutineScope(EmptyCoroutineContext)
  private val insightVisibilityFlow = MutableStateFlow(false)
  private var deprecationData =
    DevServicesDeprecationData(
      "header",
      "description",
      "url",
      true,
      DevServicesDeprecationStatus.UNSUPPORTED,
    )

  @Test
  fun `userNotified is logged only once`() {
    createPanel()
    verify(tracker, never()).logServiceDeprecated(eq(INSIGHTS_PANEL), eq(null), eq(null), eq(null))

    insightVisibilityFlow.value = true
    verify(tracker, timeout(5000).times(1))
      .logServiceDeprecated(eq(INSIGHTS_PANEL), eq(true), anyOrNull(), anyOrNull())

    insightVisibilityFlow.value = false
    verify(tracker).logServiceDeprecated(eq(INSIGHTS_PANEL), anyOrNull(), anyOrNull(), anyOrNull())

    insightVisibilityFlow.value = true
    verify(tracker).logServiceDeprecated(eq(INSIGHTS_PANEL), anyOrNull(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `moreInfo logged when user click more info url`() {
    val panel = createPanel()
    val moreInfoLabel = panel.findDescendant<HyperlinkLabel>() ?: fail("More info label not found")
    moreInfoLabel.doClick()

    verify(tracker).logServiceDeprecated(eq(INSIGHTS_PANEL), eq(null), eq(true), eq(null))
  }

  @Test
  fun `userClicked logged when user clicks update`() {
    val panel = createPanel()
    val updateLabel = panel.findDescendant<JButton>() ?: fail("Update label not found")
    updateLabel.doClick()

    verify(tracker).logServiceDeprecated(eq(INSIGHTS_PANEL), eq(null), eq(null), eq(true))
  }

  private fun createPanel() =
    InsightDeprecatedPanel(
      scope,
      projectRule.project,
      deprecationData,
      insightVisibilityFlow,
      tracker,
    )
}
