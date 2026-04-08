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
package com.android.tools.idea.vitals

import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.AiInsightContributor
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolverImpl
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.stacktrace.Caption
import com.android.tools.idea.insights.model.stacktrace.ExceptionStack
import com.android.tools.idea.insights.model.stacktrace.Frame
import com.android.tools.idea.insights.model.stacktrace.Stacktrace
import com.android.tools.idea.insights.model.stacktrace.StacktraceGroup
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VitalsAiInsightToolkitTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var aiInsightToolkit: VitalsAiInsightToolkit

  @Before
  fun setup() {
    val client =
      object : AiInsightContributor {
        override fun canContribute(): Boolean = true

        override fun isModelAvailable() = true

        override fun showOnboarding(project: Project) = Unit

        override suspend fun fetchInsight(
          connection: Connection,
          event: Event,
          project: Project,
          codeContextResolver: CodeContextResolver,
        ): AiInsight {
          return AiInsight("insight for $connection and $event", event, insightSource = InsightSource.STUDIO_BOT)
        }
      }
    ExtensionTestUtil.maskExtensions(AiInsightContributor.EP_NAME, listOf(client), projectRule.disposable)

    aiInsightToolkit = VitalsAiInsightToolkit(projectRule.project, CodeContextResolverImpl(projectRule.project))
  }

  @Test
  fun `fetch insight populates proto fields correctly`() = runBlocking {
    val insight = aiInsightToolkit.fetchInsight(TEST_CONNECTION_1, ISSUE1.id, null, ISSUE1.issueDetails.fatality, ISSUE1.sampleEvent, true)

    val rawInsight = (insight as LoadingState.Ready).value.rawInsight
    Truth.assertThat(rawInsight).isEqualTo("insight for $TEST_CONNECTION_1 and ${ISSUE1.sampleEvent}")
  }

  @Test
  fun `test fetch insight on ANR returns unsupported operation`() = runBlocking {
    val insight = aiInsightToolkit.fetchInsight(TEST_CONNECTION_1, ISSUE1.id, null, FailureType.ANR, ISSUE1.sampleEvent)

    Truth.assertThat(insight).isEqualTo(LoadingState.UnsupportedOperation("Insights are currently not available for ANRs"))
  }

  @Test
  fun `test fetch insight on native crash returns unsupported operation`() = runBlocking {
    val stackTraceGroup =
      StacktraceGroup(
        listOf(
          ExceptionStack(
            Stacktrace(Caption("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***")),
            "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
            "",
            "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
          ),
          ExceptionStack(
            Stacktrace(Caption("pid", "0, tid: 2526 >>> com.android.vending <<<")),
            "pid",
            "0, tid: 2526 >>> com.android.vending <<<",
            "pid: 0, tid: 2526 >>> com.android.vending <<<",
          ),
          ExceptionStack(
            Stacktrace(
              Caption("backtrace", ")"),
              frames = listOf(Frame(rawSymbol = "#00  pc 0x00000000001f4cdc", symbol = "#00  pc 0x00000000001f4cdc")),
            ),
            type = "backtrace",
            rawExceptionMessage = "backtrace:",
          ),
        )
      )

    val insight =
      aiInsightToolkit.fetchInsight(TEST_CONNECTION_1, ISSUE1.id, null, FailureType.FATAL, Event(stacktraceGroup = stackTraceGroup))

    Truth.assertThat(insight).isEqualTo(LoadingState.UnsupportedOperation("Insights are currently not available for native crashes"))
  }
}
