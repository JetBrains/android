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
package com.android.screenshottest.run

import com.android.screenshottest.producers.IS_SCREENSHOT_TEST_CONFIGURATION
import com.android.screenshottest.producers.IS_SCREENSHOT_UPDATE_CONFIGURATION
import com.android.tools.idea.metrics.MetricsTrackerRule
import com.android.tools.idea.testartifacts.screenshot.ScreenshotTestExecutionListener
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ScreenshotTestExecutionListenerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val metricsTrackerRule = MetricsTrackerRule()

  private lateinit var listener: ScreenshotTestExecutionListener
  private lateinit var runConfiguration: GradleRunConfiguration
  private lateinit var env: ExecutionEnvironment

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)
    listener = ScreenshotTestExecutionListener()
    runConfiguration = Mockito.mock(GradleRunConfiguration::class.java)
    env = Mockito.mock(ExecutionEnvironment::class.java)
    `when`(env.runProfile).thenReturn(runConfiguration)
    `when`(env.project).thenReturn(projectRule.project)
  }

  @Test
  fun processStarted_forRegularScreenshotTest_logsValidateClicked() {
    `when`(runConfiguration.getUserData<Boolean>(IS_SCREENSHOT_TEST_CONFIGURATION)).thenReturn(true)
    `when`(runConfiguration.getUserData<Boolean>(IS_SCREENSHOT_UPDATE_CONFIGURATION)).thenReturn(null)
    `when`(
        runConfiguration.getUserData<Boolean>(
          GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey
        )
      )
      .thenReturn(true)

    listener.processStarted("executor", env, Mockito.mock(com.intellij.execution.process.ProcessHandler::class.java))

    val usages = metricsTrackerRule.testTracker.usages
    assertThat(usages).isNotEmpty()
    val lastEvent = usages.last().studioEvent
    assertThat(lastEvent.screenshotTestComposePreviewEvent.type).isEqualTo(ScreenshotTestComposePreviewEvent.Type.VALIDATE_CLICKED)
  }

  @Test
  fun processStarted_forUpdateScreenshotTest_doesNotLogValidateClicked() {
    `when`(runConfiguration.getUserData<Boolean>(IS_SCREENSHOT_TEST_CONFIGURATION)).thenReturn(true)
    `when`(runConfiguration.getUserData<Boolean>(IS_SCREENSHOT_UPDATE_CONFIGURATION)).thenReturn(true)
    `when`(
        runConfiguration.getUserData<Boolean>(
          GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey
        )
      )
      .thenReturn(true)

    listener.processStarted("executor", env, Mockito.mock(com.intellij.execution.process.ProcessHandler::class.java))

    val usages = metricsTrackerRule.testTracker.usages
    val validateEvents =
      usages.filter { it.studioEvent.screenshotTestComposePreviewEvent.type == ScreenshotTestComposePreviewEvent.Type.VALIDATE_CLICKED }
    assertThat(validateEvents).isEmpty()
  }
}
