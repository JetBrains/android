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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.VITALS_KEY
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.ui.StackTraceConsole
import com.android.tools.idea.insights.ui.executeWithErrorProcessor
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class VitalsStackTraceConsoleTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule, VITALS_KEY)

  private val fetchState =
    LoadingState.Ready(
      IssueResponse(listOf(ISSUE1, ISSUE2), emptyList(), emptyList(), emptyList(), Permission.FULL)
    )

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  @Test
  fun `when issue is selected, correct stack trace is printed`() {
    val stackTraceConsole =
      runBlocking(AndroidDispatchers.uiThread) {
        StackTraceConsole(controllerRule.controller, projectRule.project, controllerRule.tracker)
          .apply {
            ExceptionFilters.getFilters(GlobalSearchScope.allScope(projectRule.project)).onEach {
              consoleView.addMessageFilter(it)
            }

            (consoleView.editor.foldingModel as FoldingModelImpl).isFoldingEnabled = false
          }
      }
    Disposer.register(controllerRule.disposable, stackTraceConsole)
    executeWithErrorProcessor {
      runBlocking(controllerRule.controller.coroutineScope.coroutineContext) {
        controllerRule.consumeInitialState(fetchState)
        WriteAction.run<RuntimeException>(stackTraceConsole.consoleView::flushDeferredText)
        stackTraceConsole.consoleView.waitAllRequests()

        Truth.assertThat(stackTraceConsole.consoleView.editor.document.text.trim())
          .isEqualTo(
            """
             retrofit2.HttpException: HTTP 401 
                 dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.build(ResponseWrapper.kt:23)
                 dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.fetchOrError(ResponseWrapper.kt:31)
          """
              .trimIndent()
          )
      }
    }
  }
}
