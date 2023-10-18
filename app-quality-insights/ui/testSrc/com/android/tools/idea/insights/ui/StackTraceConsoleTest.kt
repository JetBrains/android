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
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import java.util.EnumSet
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class StackTraceConsoleTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  private val fetchState =
    LoadingState.Ready(
      IssueResponse(listOf(ISSUE1, ISSUE2), emptyList(), emptyList(), emptyList(), Permission.FULL)
    )

  private lateinit var stackTraceConsole: StackTraceConsole

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  @Before
  fun setUp() {
    stackTraceConsole =
      runBlocking(AndroidDispatchers.uiThread) {
        StackTraceConsole(controllerRule.controller, projectRule.project, controllerRule.tracker)
          .apply {
            ExceptionFilters.getFilters(GlobalSearchScope.allScope(projectRule.project)).onEach {
              consoleView.addMessageFilter(it)
            }
          }
      }
    Disposer.register(controllerRule.disposable, stackTraceConsole)
  }

  private val editor
    get() = stackTraceConsole.consoleView.editor

  @Test
  fun `stack trace is printed when issue selection is updated`() {
    executeWithErrorProcessor {
      runBlocking(controllerRule.controller.coroutineScope.coroutineContext) {
        controllerRule.consumeInitialState(fetchState)
        WriteAction.run<RuntimeException>(stackTraceConsole.consoleView::flushDeferredText)
        stackTraceConsole.consoleView.waitAllRequests()

        assertThat(editor.document.text.trim())
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

  @Test
  fun `stack trace is re-highlighted after project sync`() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/ResponseWrapper.kt",
        """
            package dev.firebase.appdistribution.api_service
            class ResponseWrapper {
              companion object
               {
                  fun build()
               }
            }
          """
          .trimIndent()
      )

    executeWithErrorProcessor {
      runBlocking(controllerRule.controller.coroutineScope.coroutineContext) {
        controllerRule.consumeInitialState(fetchState)

        WriteAction.run<RuntimeException>(stackTraceConsole.consoleView::flushDeferredText)
        stackTraceConsole.consoleView.waitAllRequests()

        // Ensure initial state: there's hyperlinks
        val hyperlinks = stackTraceConsole.consoleView.hyperlinks
        waitForCondition(3000) {
          // Below is what's printed out in the console:
          // ```
          //  retrofit2.HttpException: HTTP 401
          // dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.build(ResponseWrapper.kt:23)
          // dev.firebase.appdistribution.api_service.ResponseWrapper${'$'}Companion.fetchOrError(ResponseWrapper.kt:31)
          // ```
          hyperlinks.findAllHyperlinksOnLine(1).isNotEmpty() &&
            hyperlinks.findAllHyperlinksOnLine(2).isNotEmpty()
        }

        WriteAction.run<RuntimeException>(file::delete)
        // Sync and then the console will be re-highlighted: no hyperlinks anymore.
        projectRule.project.messageBus
          .syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC)
          .syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
        dispatchAllInvocationEventsInIdeEventQueue()

        waitForCondition(3000) {
          hyperlinks.findAllHyperlinksOnLine(1).isEmpty() &&
            hyperlinks.findAllHyperlinksOnLine(2).isEmpty()
        }
      }
    }
  }

  private fun executeWithErrorProcessor(job: () -> Unit) {
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

    LoggedErrorProcessor.executeWith<Throwable>(errorProcessor, job)
    assertThat(error).isNull()
  }
}
