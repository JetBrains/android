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
package com.android.tools.idea.profilers.leakcanary

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.GeminiPluginApiV2
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.leakcanarylib.data.Leak
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.mockito.Mockito

class LeakCanaryAiHandlerTest {

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var mockGeminiApi: GeminiPluginApi
  private val project: Project get() = projectRule.project

  @Before
  fun setup() {
    mockGeminiApi = mock(GeminiPluginApi::class.java)
    `when`(mockGeminiApi.MAX_QUERY_CHARS).thenReturn(Int.MAX_VALUE)

    // Register the mock GeminiPluginApi extension
    val ep = GeminiPluginApi.EP_NAME.getPoint(null)
    ep.registerExtension(mockGeminiApi, projectRule.project)
  }

  @After
  fun tearDown() {
    StudioFlags.STUDIOBOT_V2_UI_ENABLED.clearOverride()
  }

  @Test
  fun `test analyzeLeakWithStudioBot sends query to Gemini`() {
    StudioFlags.STUDIOBOT_V2_UI_ENABLED.override(false)

    val rawTrace = "Test Trace"
    val leak = mock(Leak::class.java)

    LeakCanaryAiHandler.getInstance(project).analyzeLeakWithStudioBot(rawTrace, leak)
    val promptCaptor = argumentCaptor<LlmPrompt>()
    val displayTextCaptor = argumentCaptor<String>()

    // Using timeout as a safety net since we are dealing with asynchronous background tasks.
    // We also need to pump the event queue so that Application.invokeLater can run.
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < 5000) {
      // 1. Give the UI thread a chance to run any pending tasks.
      ApplicationManager.getApplication().invokeAndWait {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
      // 2. DETECT: Check if Mockito has recorded ANY calls to 'sendChatQuery'.
      if (Mockito.mockingDetails(mockGeminiApi).invocations.any { it.method.name == "sendChatQuery" }) {
        // 3. SUCCESS: The background task finished! We can exit the loop.
        break
      }
      // 4. WAIT: If not called yet, sleep for 100ms and try again.
      Thread.sleep(100)
    }

    verify(mockGeminiApi).sendChatQuery(
      any(),
      promptCaptor.capture(),
      displayTextCaptor.capture(),
      eq(GeminiPluginApi.RequestSource.OTHER)
    )
    // TODO: Change to GeminiPluginApi.RequestSource.PROFILER once it is added in the ml-api module.

    assertTrue(displayTextCaptor.firstValue.contains(rawTrace))
  }

  @Test
  fun `test analyzeLeakWithStudioBot sends query to Gemini V2`() = runBlocking {
    StudioFlags.STUDIOBOT_V2_UI_ENABLED.override(true)

    val mockGeminiApiV2 = mock(GeminiPluginApiV2::class.java)
    val epV2 = GeminiPluginApiV2.EP_NAME.getPoint(null)
    epV2.registerExtension(mockGeminiApiV2, projectRule.project)

    val rawTrace = "Test Trace"
    val leak = mock(Leak::class.java)

    LeakCanaryAiHandler.getInstance(project).analyzeLeakWithStudioBot(rawTrace, leak)
    val queryCaptor = argumentCaptor<String>()

    // We pump the event queue so that Application.invokeLater can run.
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < 5000) {
      ApplicationManager.getApplication().invokeAndWait {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
      if (Mockito.mockingDetails(mockGeminiApiV2).invocations.any { it.method.name == "submitQueryInToolWindow" }) {
        break
      }
      Thread.sleep(100)
    }

    verify(mockGeminiApiV2).submitQueryInToolWindow(
      eq(project),
      queryCaptor.capture(),
      any(),
      any(),
      any()
    )

    val submittedQuery = queryCaptor.firstValue
    assertTrue(submittedQuery.contains("Fix this memory leak and summarize the outcome:"))
    assertTrue(submittedQuery.contains(rawTrace))
    assertFalse(submittedQuery.contains("Role"))
  }
}
