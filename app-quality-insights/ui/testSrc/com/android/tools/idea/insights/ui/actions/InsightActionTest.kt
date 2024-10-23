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
package com.android.tools.idea.insights.ui.actions

import com.android.mockito.kotlin.whenever
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventData
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ui.FakeGeminiPluginApi
import com.android.tools.idea.insights.ui.REQUEST_SOURCE_KEY
import com.android.tools.idea.insights.ui.SELECTED_EVENT_KEY
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import java.util.concurrent.CountDownLatch
import javax.swing.JButton
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.MockedStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsightActionTest {

  @get:Rule val projectRule = ProjectRule()
  private val eventList = List(3) { createAppInsightEvent(it) }

  private val scope = CoroutineScope(EmptyCoroutineContext)
  private var isGeminiDisabled = false
  private val mockGeminiPlugin = mock<IdeaPluginDescriptor>()
  private lateinit var mockPluginManagerCore: MockedStatic<PluginManagerCore>
  private var eventIdx: Int = 0

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setup() {
    eventIdx = 0
    mockPluginManagerCore = mockStatic(projectRule.disposable)
    whenever(mockGeminiPlugin.name).thenReturn("Gemini")
    whenever(mockGeminiPlugin.pluginId).thenAnswer { PluginId.getId("") }
    mockPluginManagerCore
      .whenever<Any> { PluginManagerCore.isDisabled(any()) }
      .thenAnswer { isGeminiDisabled }
    mockPluginManagerCore
      .whenever<Any> { PluginManagerCore.plugins }
      .thenAnswer { arrayOf(mockGeminiPlugin) }

    fakeGeminiPluginApi = FakeGeminiPluginApi()
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `insight action opens Gemini toolwindow if not open`() {
    fakeGeminiPluginApi.available = false
    val insightButton = createInsightButton()
    assertThat(insightButton.text).isEqualTo("Enable insights")
    assertThat(insightButton.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")
    InsightAction.actionPerformed(createTestEvent())
    assertThat(fakeGeminiPluginApi.stagedPrompt).isNotNull()
  }

  @Test
  fun `insight action changes text when onboarding state changes`() {
    fakeGeminiPluginApi.available = false
    val insightButton = createInsightButton()
    assertThat(insightButton.text).isEqualTo("Enable insights")
    assertThat(insightButton.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")

    fakeGeminiPluginApi.available = true
    InsightAction.update(insightButton)

    assertThat(insightButton.text).isEqualTo("Show insights")
    assertThat(insightButton.toolTipText).isEqualTo("Show insights for this issue")

    fakeGeminiPluginApi.available = false
    InsightAction.update(insightButton)

    assertThat(insightButton.text).isEqualTo("Enable insights")
    assertThat(insightButton.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")
  }

  @Test
  fun `insight action stages prompt when studio bot available`() {
    val insightButton = createInsightButton()
    fakeGeminiPluginApi.available = true
    InsightAction.update(insightButton)

    assertThat(insightButton.text).isEqualTo("Show insights")
    assertThat(insightButton.toolTipText).isEqualTo("Show insights for this issue")

    InsightAction.actionPerformed(createTestEvent())

    val expectedPrompt = createExpectedPrompt(0)
    assertThat(fakeGeminiPluginApi.stagedPrompt).isEqualTo(expectedPrompt)

    // Simulate navigation of event using left/right arrow buttons
    eventIdx += 1
    InsightAction.actionPerformed(createTestEvent())

    val newExpectedPrompt = createExpectedPrompt(1)
    assertThat(fakeGeminiPluginApi.stagedPrompt).isEqualTo(newExpectedPrompt)
  }

  @Test
  fun `insight action opens plugin window when gemini plugin not enabled`() {
    fakeGeminiPluginApi.available = false
    isGeminiDisabled = true
    val countDownLatch = CountDownLatch(1)

    val mockPluginManagerConfigurable =
      mockStatic<PluginManagerConfigurable>(projectRule.disposable)
    mockPluginManagerConfigurable
      .whenever<Any> { PluginManagerConfigurable.showPluginConfigurable(any(), anyList()) }
      .thenAnswer { countDownLatch.countDown() }
    val insightAction = createInsightButton()

    assertThat(insightAction.text).isEqualTo("Enable insights")
    assertThat(insightAction.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")

    InsightAction.actionPerformed(createTestEvent())
    assertThat(countDownLatch.count).isEqualTo(0)
  }

  private fun createAppInsightEvent(idx: Int) =
    Event(
      name = "event",
      eventData = EventData(Device("manufacturer", "model"), OperatingSystemInfo("14", "")),
      stacktraceGroup = createStackTraceGroup(idx),
    )

  private fun createInsightButton() =
    (InsightAction.createCustomComponent(InsightAction.templatePresentation, "") as JButton).also {
      InsightAction.update(it)
    }

  private fun createStackTraceGroup(eventIdx: Int) =
    StacktraceGroup(List(5) { createRandomException(it, eventIdx) })

  private fun createRandomException(idx: Int, eventIdx: Int): ExceptionStack {
    return ExceptionStack(
      createStackTrace(),
      rawExceptionMessage =
        "${if(idx == 1) "Caused by " else ""}rawExceptionMessage: Some Exception Message for event $eventIdx - ${idx+1}",
    )
  }

  private fun createExpectedPrompt(eventIdx: Int) =
    "Explain this exception from my app running on manufacturer model with Android version 14:\n" +
      "Exception:\n" +
      "```\n" +
      "rawExceptionMessage: Some Exception Message for event $eventIdx - 1\n" +
      "\tframe-0\n" +
      "\tframe-1\n" +
      "\tframe-2\n" +
      "\tframe-3\n" +
      "\tframe-4\n" +
      "Caused by rawExceptionMessage: Some Exception Message for event $eventIdx - 2\n" +
      "\tframe-0\n" +
      "\tframe-1\n" +
      "\tframe-2\n" +
      "\tframe-3\n" +
      "\tframe-4\n" +
      "```"

  private fun createStackTrace() = Stacktrace(frames = List(5) { Frame(rawSymbol = "frame-$it") })

  private fun InsightAction.update(button: JButton) =
    updateCustomComponent(button, templatePresentation)

  private fun createTestEvent() =
    AnActionEvent.createFromAnAction(InsightAction, null, "") { dataId ->
      when {
        REQUEST_SOURCE_KEY.`is`(dataId) -> GeminiPluginApi.RequestSource.CRASHLYTICS
        SELECTED_EVENT_KEY.`is`(dataId) -> eventList[eventIdx]
        CommonDataKeys.PROJECT.`is`(dataId) -> projectRule.project
        else -> null
      }
    }
}
