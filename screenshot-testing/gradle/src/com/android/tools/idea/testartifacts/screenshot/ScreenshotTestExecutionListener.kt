/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.testartifacts.screenshot

import com.android.screenshottest.producers.IS_SCREENSHOT_TEST_CONFIGURATION
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class ScreenshotTestExecutionListener : ExecutionListener {
  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val runProfile = env.runProfile
    if (runProfile is GradleRunConfiguration &&
        runProfile.getUserData<Boolean>(IS_SCREENSHOT_TEST_CONFIGURATION) == true &&
        runProfile.getUserData<Boolean>(GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey) == true) {
      UsageTracker.log(
        AndroidStudioEvent.newBuilder().apply {
          kind = AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW
          screenshotTestComposePreviewEvent = ScreenshotTestComposePreviewEvent.newBuilder().apply {
            type = ScreenshotTestComposePreviewEvent.Type.VALIDATE_CLICKED
          }.build()
        }.withProjectId(env.project)
      )
    }
  }
}