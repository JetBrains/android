/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.insights.ai.FakeAiInsightToolkit
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.AI_INSIGHT_TOOLKIT_KEY
import com.android.tools.idea.insights.ui.APP_INSIGHTS_TRACKER_KEY
import com.android.tools.idea.insights.ui.SELECTED_APP_ID_KEY
import com.android.tools.idea.ui.resourcemanager.actions.HeaderAction
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginUsersRule
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.GenerateInsightsAction.Action
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent.createTestEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class InsightSettingGroupTest {

  private val projectRule = ProjectRule()
  private val loginUserRule = LoginUsersRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(loginUserRule)

  private lateinit var fakeToolkit: FakeAiInsightToolkit

  @Before
  fun setUp() {
    loginUserRule.setActiveUser("test_user@google.com")
    fakeToolkit = FakeAiInsightToolkit(projectRule.project)
  }

  @Test
  fun `test setting group properties`() {
    val group = InsightSettingGroup()

    assertThat(group.isPopup).isTrue()

    val event = createTestEvent()
    group.update(event)
    assertThat(event.presentation.icon).isEqualTo(AllIcons.General.Settings)
  }

  @Test
  fun `test setting group children`() {
    val group = InsightSettingGroup()

    val children = group.getChildren(null)
    assertThat(children).hasLength(2)
    assertThat(children[0]).isInstanceOf(HeaderAction::class.java)
    assertThat(children[1]).isInstanceOf(InsightAutoGenerateSetting::class.java)
  }

  @Test
  fun `test auto generate setting`() {
    val setting = InsightAutoGenerateSetting()
    val mockTracker = mock(AppInsightsTracker::class.java)
    val dataContext =
      SimpleDataContext.builder()
        .add(AI_INSIGHT_TOOLKIT_KEY, fakeToolkit)
        .add(APP_INSIGHTS_TRACKER_KEY, mockTracker)
        .add(SELECTED_APP_ID_KEY, "test_app_id")
        .build()

    fakeToolkit.setAutoGenerate(true)
    assertThat(setting.isSelected(createTestEvent(dataContext))).isTrue()

    fakeToolkit.setAutoGenerate(false)
    assertThat(setting.isSelected(createTestEvent(dataContext))).isFalse()

    setting.actionPerformed(createTestEvent(dataContext))
    assertThat(fakeToolkit.isAutoGenerateEnabled()).isTrue()
    verify(mockTracker).logGenerateInsightAction("test_app_id", Action.ENABLE_AUTO_GENERATE)

    setting.actionPerformed(createTestEvent(dataContext))
    assertThat(fakeToolkit.isAutoGenerateEnabled()).isFalse()
    verify(mockTracker).logGenerateInsightAction("test_app_id", Action.DISABLE_AUTO_GENERATE)
  }

  @Test
  fun `test auto generate setting closes popup when clicked`() {
    val setting = InsightAutoGenerateSetting()

    assertThat(setting.templatePresentation.keepPopupOnPerform).isEqualTo(KeepPopupOnPerform.Never)
  }
}
