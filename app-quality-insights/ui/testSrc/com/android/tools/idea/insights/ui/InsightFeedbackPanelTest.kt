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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.AqiExperimentsConfig
import com.android.tools.idea.serverflags.protos.ExperimentType
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class InsightFeedbackPanelTest {

  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  private lateinit var fakeUi: FakeUi
  private val mockServerFlagService = mock<ServerFlagService>()

  @Before
  fun setup() = runBlocking {
    withContext(AndroidDispatchers.uiThread) { fakeUi = FakeUi(InsightFeedbackPanel()) }
    application.replaceService(
      ServerFlagService::class.java,
      mockServerFlagService,
      projectRule.disposable,
    )
  }

  @Test
  fun `test upvote and downvote actions`() = runBlocking {
    val (upvote, downvote) = fakeUi.findAllComponents<ActionButton>()
    assertThat(upvote.icon).isEqualTo(AllIcons.Ide.Like)
    assertThat(upvote.presentation.text).isEqualTo("Upvote this insight")
    assertThat(upvote.isSelected).isFalse()

    assertThat(downvote.icon).isEqualTo(AllIcons.Ide.Dislike)
    assertThat(downvote.presentation.text).isEqualTo("Downvote this insight")
    assertThat(upvote.isSelected).isFalse()

    val upvoteEvent = TestActionEvent.createTestEvent()
    val downvoteEvent = TestActionEvent.createTestEvent()

    upvote.actionPerformed(upvoteEvent)
    downvote.updateAction(downvoteEvent)
    assertThat(upvoteEvent.isSelected).isTrue()
    assertThat(downvoteEvent.isSelected).isFalse()

    downvote.actionPerformed(downvoteEvent)
    upvote.updateAction(upvoteEvent)
    assertThat(upvoteEvent.isSelected).isFalse()
    assertThat(downvoteEvent.isSelected).isTrue()

    downvote.actionPerformed(downvoteEvent)
    upvote.updateAction(upvoteEvent)
    assertThat(upvoteEvent.isSelected).isFalse()
    assertThat(downvoteEvent.isSelected).isFalse()
  }

  @Test
  fun `test sentiment tracked when feedback clicked`() = runBlocking {
    val (upvote, downvote) = fakeUi.findAllComponents<ActionButton>()

    whenever(mockServerFlagService.getProto(anyString(), any()))
      .thenReturn(createProtoResponse(ExperimentType.CONTROL))

    val upvoteEvent =
      TestActionEvent.createTestEvent { key ->
        when (key) {
          APP_INSIGHTS_TRACKER_KEY.name -> controllerRule.tracker
          FAILURE_TYPE_KEY.name -> FailureType.ANR
          else -> null
        }
      }
    upvote.actionPerformed(upvoteEvent)
    verify(controllerRule.tracker)
      .logInsightSentiment(
        AppQualityInsightsUsageEvent.InsightSentiment.Sentiment.THUMBS_UP,
        AppQualityInsightsUsageEvent.InsightSentiment.Experiment.CONTROL,
        AppQualityInsightsUsageEvent.CrashType.ANR,
      )

    whenever(mockServerFlagService.getProto(anyString(), any()))
      .thenReturn(createProtoResponse(ExperimentType.TOP_SOURCE))
    val downvoteEvent =
      TestActionEvent.createTestEvent { key ->
        when (key) {
          APP_INSIGHTS_TRACKER_KEY.name -> controllerRule.tracker
          FAILURE_TYPE_KEY.name -> FailureType.FATAL
          else -> null
        }
      }
    downvote.actionPerformed(downvoteEvent)
    verify(controllerRule.tracker)
      .logInsightSentiment(
        AppQualityInsightsUsageEvent.InsightSentiment.Sentiment.THUMBS_DOWN,
        AppQualityInsightsUsageEvent.InsightSentiment.Experiment.TOP_SOURCE,
        AppQualityInsightsUsageEvent.CrashType.FATAL,
      )
  }

  private val AnActionEvent.isSelected: Boolean
    get() = Toggleable.isSelected(presentation)

  private fun ActionButton.updateAction(e: AnActionEvent) = action.update(e)

  private fun ActionButton.actionPerformed(e: AnActionEvent) {
    action.actionPerformed(e)
    action.update(e)
  }

  private fun createProtoResponse(experiment: ExperimentType) =
    AqiExperimentsConfig.newBuilder().setExperimentType(experiment).build()
}
