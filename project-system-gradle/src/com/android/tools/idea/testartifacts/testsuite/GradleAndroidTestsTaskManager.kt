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
package com.android.tools.idea.testartifacts.testsuite

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.flags.StudioFlags.AGP_TEST_SUITES_ENABLED
import com.android.tools.idea.flags.StudioFlags.ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.UIUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * Gradle Task Manager for configuring Android test tasks.
 */
class GradleAndroidTestsTaskManager : GradleTaskManagerExtension  {
  override fun configureTasks(projectPath: String,
                              id: ExternalSystemTaskId,
                              settings: GradleExecutionSettings,
                              gradleVersion: GradleVersion?) {
    if (ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.get() &&
        (settings.getUserData(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey) == true)) {
      settings.addInitScript(
        "addTestListenerForAndroidTestSuiteView",
        //language=groovy
        """
          gradle.taskGraph.whenReady { taskGraph ->
            taskGraph.allTasks.each { Task task ->
              if (task instanceof Test) {
                task.jvmArgs += '-DPreviewScreenshotTestEngineInput.ReportEntrySetting.redirectToStdout=true'
              }
            }
          }
        """.trimIndent())
    }

    if ((ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.get() || AGP_TEST_SUITES_ENABLED.get()) &&
        settings.getUserData(DeployableToDevice.KEY) == true) {
      id.findProject()?.takeIf { IdeInfo.getInstance().isAndroidStudio }?.let { project ->
        val deviceSerials = launchDevices(project)
        if (deviceSerials.isEmpty()) {
          UIUtil.invokeLaterIfNeeded {
            Messages.showErrorDialog(
              "To run this configuration, select a device from the dropdown. If no devices are available, please configure a new one.",
              "No Device Found",
            )
          }
          throw ProcessCanceledException() // Stops the execution.
        }

        settings.withEnvironmentVariables(mapOf("ANDROID_SERIAL" to deviceSerials.joinToString(",")))
      }
    }
  }
}
