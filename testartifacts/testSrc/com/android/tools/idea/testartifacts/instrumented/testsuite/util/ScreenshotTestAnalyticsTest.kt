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
package com.android.tools.idea.testartifacts.instrumented.testsuite.util

import com.android.tools.idea.metrics.MetricsTrackerRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

class ScreenshotTestAnalyticsTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val metricsTrackerRule = MetricsTrackerRule()

  @Test
  fun logScreenshotTestEvent_logsCorrectEvent() {
    val project = projectRule.project
    val eventType = ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_OPEN

    logScreenshotTestEvent(eventType, project)

    val usages = metricsTrackerRule.testTracker.usages
    assertThat(usages).isNotEmpty()

    val lastEvent = usages.last().studioEvent
    assertThat(lastEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW)
    assertThat(lastEvent.screenshotTestComposePreviewEvent.type).isEqualTo(eventType)
  }

  @Test
  fun toolbarAnalyticsOnlyLogsOncePerSession() {
    val analytics = ScreenshotToolbarAnalytics(projectRule.project)

    analytics.logAction()
    analytics.logAction()
    analytics.logAction()

    val usages = metricsTrackerRule.testTracker.usages
    val toolbarEvents =
      usages.filter {
        it.studioEvent.screenshotTestComposePreviewEvent.type == ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_TOOLBAR_ACTION
      }

    assertThat(toolbarEvents).hasSize(1)
  }

  @Test
  fun loggedActionTriggersAnalyticsAndDelegate() {
    var delegateCalled = false
    val delegate =
      object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
          delegateCalled = true
        }
      }
    val analytics = ScreenshotToolbarAnalytics(projectRule.project)
    val loggedAction = LoggedAction(delegate, analytics)

    loggedAction.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(delegateCalled).isTrue()
    val usages = metricsTrackerRule.testTracker.usages
    assertThat(
        usages.any {
          it.studioEvent.screenshotTestComposePreviewEvent.type == ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_TOOLBAR_ACTION
        }
      )
      .isTrue()
  }

  @Test
  fun loggedToggleActionTriggersAnalyticsAndDelegate() {
    var delegateValue = false
    val delegate =
      object : ToggleAction("test", "desc", null) {
        override fun isSelected(e: AnActionEvent): Boolean = delegateValue

        override fun setSelected(e: AnActionEvent, state: Boolean) {
          delegateValue = state
        }
      }
    val analytics = ScreenshotToolbarAnalytics(projectRule.project)
    val loggedToggle = LoggedToggleAction(delegate, analytics)

    loggedToggle.setSelected(TestActionEvent.createTestEvent(), true)

    assertThat(delegateValue).isTrue()
    val usages = metricsTrackerRule.testTracker.usages
    assertThat(
        usages.any {
          it.studioEvent.screenshotTestComposePreviewEvent.type == ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_TOOLBAR_ACTION
        }
      )
      .isTrue()
  }
}
