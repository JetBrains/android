/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.ProjectRule
import java.util.EnumSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class StackTracePanelTest {
  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)
  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  @Test
  fun `can switch issues quickly`() {
    var error: String? = null
    val errorProcessor =
      object : LoggedErrorProcessor() {
        override fun processError(
          category: String,
          message: String,
          details: Array<out String>,
          t: Throwable?,
        ): Set<Action> {
          error = message
          return EnumSet.allOf(Action::class.java)
        }
      }
    LoggedErrorProcessor.executeWith<Throwable>(errorProcessor) {
      val issues = listOf(ISSUE1, ISSUE2)
      val flow = MutableSharedFlow<LoadingState<Selection<AppInsightsIssue>>>()
      val panel = invokeAndWaitIfNeeded {
        StackTracePanel(
          controllerRule.controller,
          flow,
          projectRule.project,
          {},
          controllerRule.disposable,
          controllerRule.tracker
        ) { _, _, _, _ ->
          "testuri"
        }
      }

      runBlocking(controllerRule.controller.coroutineScope.coroutineContext) {
        flow.emit(LoadingState.Ready(Selection(issues[1], issues)))
        flow.emit(LoadingState.Ready(Selection(issues[0], issues)))

        waitForCondition(5000) { panel.consoleView.editor.foldingModel.allFoldRegions.isNotEmpty() }
      }
    }
    assertThat(error).isNull()
  }

  @Test
  fun `open close issue button reacts to offline mode changes`() =
    runBlocking<Unit>(AndroidDispatchers.uiThread) {
      val fetchState =
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1, ISSUE2),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.FULL
          )
        )
      val panel = invokeAndWaitIfNeeded {
        StackTracePanel(
          controllerRule.controller,
          controllerRule.controller.state
            .map { it.issues.map { timed -> timed.value } }
            .distinctUntilChanged(),
          projectRule.project,
          {},
          controllerRule.disposable,
          controllerRule.tracker
        ) { _, _, _, _ ->
          "testuri"
        }
      }

      // Initially, the button is enabled.
      controllerRule.consumeInitialState(fetchState)
      assertThat(panel.toggleButton.isEnabled).isTrue()

      // Go offline and verify button is disabled.
      controllerRule.enterOfflineMode()
      controllerRule.consumeNext()
      controllerRule.consumeFetchState(state = fetchState)
      waitForCondition { !panel.toggleButton.isEnabled }

      // Go online and verify button is enabled.
      controllerRule.refreshAndConsumeLoadingState()
      controllerRule.consumeFetchState(state = fetchState, isTransitionToOnlineMode = true)
      waitForCondition { panel.toggleButton.isEnabled }
    }
}
