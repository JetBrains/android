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

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.project.Project

/** Logs a screenshot test event with the specified type. */
fun logScreenshotTestEvent(type: ScreenshotTestComposePreviewEvent.Type, project: Project?) {
  val event = ScreenshotTestComposePreviewEvent.newBuilder().setType(type).build()

  val studioEvent =
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW)
      .setScreenshotTestComposePreviewEvent(event)
      .withProjectId(project)

  UsageTracker.log(studioEvent)
}
