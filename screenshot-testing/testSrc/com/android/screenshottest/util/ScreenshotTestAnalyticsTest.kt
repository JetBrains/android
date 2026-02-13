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
package com.android.screenshottest.util

import com.android.tools.idea.metrics.MetricsTrackerRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenshotTestAnalyticsTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val metricsTrackerRule = MetricsTrackerRule()

  @Test
  fun testLogScreenshotTestEvent() {
    val project = projectRule.project
    val eventType = ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_OPEN

    logScreenshotTestEvent(eventType, project)

    val usages = metricsTrackerRule.testTracker.usages
    assertTrue("Usage should not be empty", usages.isNotEmpty())

    val lastEvent = usages.last().studioEvent
    assertEquals(AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW, lastEvent.kind)
    assertEquals(eventType, lastEvent.screenshotTestComposePreviewEvent.type)
  }
}
