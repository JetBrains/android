/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.insight

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.ai.AgentAction
import com.android.tools.idea.insights.ai.AgentActionContributor
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.HyperlinkLabel
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InsightLinksPanelTest {

  private val projectRule = ProjectRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(EdtRule()).around(projectRule)

  private val controller: AppInsightsProjectLevelController = mock()
  private val tracker: AppInsightsTracker = mock()
  private val contributor: AgentActionContributor = mock()
  private val currentInsightFlow = MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))
  private val stateFlow =
    MutableStateFlow(
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
      )
    )

  @Before
  fun setup() {
    whenever(controller.project).thenReturn(projectRule.project)
    whenever(controller.state).thenReturn(stateFlow)
    ExtensionTestUtil.maskExtensions(AgentActionContributor.EP_NAME, listOf(contributor), projectRule.disposable)
  }

  @Test
  @RunsInEdt
  fun `test provideActions is called correctly`() {
    val event = ISSUE1.sampleEvent
    val actions = listOf(AgentAction("Action 1", AppQualityInsightsUsageEvent.AgentActionDetails.ActionType.FIX) {})
    whenever(contributor.provideActions(any(), any(), any())).thenReturn(actions)

    val panel = InsightLinksPanel(controller, currentInsightFlow, tracker, projectRule.disposable)
    val fakeUi = FakeUi(panel)

    // Set insight, now it should call provideActions
    val insight = DEFAULT_AI_INSIGHT.copy(event = event)
    currentInsightFlow.value = LoadingState.Ready(insight)

    waitForCondition(5.seconds) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      fakeUi.layout()
      fakeUi.findComponent<HyperlinkLabel> { it.text == "Action 1" } != null
    }

    verify(contributor, times(1)).provideActions(event, ISSUE1, projectRule.project)
  }

  @Test
  @RunsInEdt
  fun `test metrics are logged when action is clicked`() {
    val event = ISSUE1.sampleEvent
    val actionCalled = mutableListOf<String>()
    val actions =
      listOf(AgentAction("Action 1", AppQualityInsightsUsageEvent.AgentActionDetails.ActionType.FIX) { actionCalled.add("Action 1") })
    whenever(contributor.provideActions(any(), any(), any())).thenReturn(actions)

    val panel = InsightLinksPanel(controller, currentInsightFlow, tracker, projectRule.disposable)
    val fakeUi = FakeUi(panel)
    currentInsightFlow.value = LoadingState.Ready(DEFAULT_AI_INSIGHT.copy(event = event))

    waitForCondition(5.seconds) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      fakeUi.layout()
      fakeUi.findComponent<HyperlinkLabel> { it.text == "Action 1" } != null
    }

    val link = fakeUi.findComponent<HyperlinkLabel> { it.text == "Action 1" }!!
    link.doClick()

    assertThat(actionCalled).containsExactly("Action 1")
    verify(tracker)
      .logAgentAction(AppQualityInsightsUsageEvent.AgentActionDetails.ActionType.FIX, CONNECTION1.appId, ISSUE1.issueDetails.fatality)
  }
}
