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
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.ProjectRule
import java.util.EnumSet
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class StackTraceConsoleTest {
  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)
  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  @Test
  fun `stack trace is printed when issue selection is updated`() =
    runBlocking<Unit> {
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
        val stackTraceConsole =
          runBlocking(AndroidDispatchers.uiThread) {
            StackTraceConsole(
              controllerRule.controller,
              projectRule.project,
              controllerRule.tracker
            )
          }
        Disposer.register(controllerRule.disposable, stackTraceConsole)
        runBlocking(controllerRule.controller.coroutineScope.coroutineContext) {
          controllerRule.consumeInitialState(fetchState)

          waitForCondition(5000) {
            stackTraceConsole.consoleView.editor.foldingModel.allFoldRegions.isNotEmpty()
          }
        }
      }
      assertThat(error).isNull()
    }
}
