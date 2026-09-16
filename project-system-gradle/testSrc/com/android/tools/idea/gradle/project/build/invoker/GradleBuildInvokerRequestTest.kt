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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.task.ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class GradleBuildInvokerRequestTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  val rootProjectPath: File
    get() = File(projectRule.project.basePath)

  val listener: ExternalSystemTaskNotificationListener = mock()

  @Test
  fun requestDefaultValues() {
    val request = GradleBuildInvoker.Request.builder(projectRule.project, rootProjectPath, ":app:assembleDebug").build()

    assertThat(request.gradleTasks).containsExactly(":app:assembleDebug")
    assertThat(request.mode).isNull()
    assertThat(request.jvmArguments).isEmpty()
    assertThat(request.commandLineArguments).isEmpty()
    assertThat(request.env).isEmpty()
    assertThat(request.isPassParentEnvs).isTrue()
    assertThat(request.doNotShowBuildOutputOnFailure).isFalse()
    assertThat(request.isWaitForCompletion).isFalse()
    assertThat(request.executionEnvironment).isNull()
    assertThat(request.listener).isNull()

    request.toExecutionSettings().let {
      assertThat(it.tasks).containsExactly(":app:assembleDebug")
      assertThat(it.jvmArguments).isEmpty()
      assertThat(it.arguments).isEmpty()
      assertThat(it.env).isEmpty()
      assertThat(it.isPassParentEnvs).isTrue()
    }
  }

  @Test
  fun requestBuilderWithAllNewValues() {
    val request =
      GradleBuildInvoker.Request.builder(projectRule.project, rootProjectPath, ":app:assembleDebug")
        .setMode(BuildMode.ASSEMBLE)
        .setCommandLineArguments(listOf("-PmyArgument=true"))
        .setJvmArguments(listOf("-DmyArgument=true"))
        .withEnvironmentVariables(mapOf("ENV_VAR" to "value"))
        .passParentEnvs(false)
        .setDoNotShowBuildOutputOnFailure(true)
        .waitForCompletion()
        .setListener(listener)
        .build()

    assertThat(request.gradleTasks).containsExactly(":app:assembleDebug")
    assertThat(request.mode).isEqualTo(BuildMode.ASSEMBLE)
    assertThat(request.commandLineArguments).containsExactly("-PmyArgument=true")
    assertThat(request.jvmArguments).containsExactly("-DmyArgument=true")
    assertThat(request.env).isEqualTo(mapOf("ENV_VAR" to "value"))
    assertThat(request.isPassParentEnvs).isFalse()
    assertThat(request.doNotShowBuildOutputOnFailure).isTrue()
    assertThat(request.isWaitForCompletion).isTrue()
    assertThat(request.executionEnvironment).isNull()
    assertThat(request.listener).isEqualTo(listener)

    request.toExecutionSettings().let {
      assertThat(it.tasks).containsExactly(":app:assembleDebug")
      assertThat(it.arguments).containsExactly("-PmyArgument=true")
      assertThat(it.jvmArguments).containsExactly("-DmyArgument=true")
      assertThat(it.env).isEqualTo(mapOf("ENV_VAR" to "value"))
      assertThat(it.isPassParentEnvs).isFalse()
    }
  }

  @Test
  fun verifyChangesToCreatedSettingsDoNotAffectOriginalData() {
    val request = GradleBuildInvoker.Request.builder(projectRule.project, rootProjectPath, ":app:assembleDebug").build()

    val settings1 = request.toExecutionSettings()
    settings1.withArguments(listOf("-PmyArgument=true"))
    settings1.withVmOption("-DmyVmOption")
    val settings2 = request.toExecutionSettings()

    assertThat(settings1.tasks).containsExactly(":app:assembleDebug")
    assertThat(settings1.arguments).containsExactly("-PmyArgument=true")
    assertThat(settings1.jvmArguments).containsExactly("-DmyVmOption")
    assertThat(settings2.tasks).containsExactly(":app:assembleDebug")
    assertThat(settings2.arguments).isEmpty()
    assertThat(settings2.jvmArguments).isEmpty()
  }

  @Test
  fun copyRequest() {
    val originalRequest =
      GradleBuildInvoker.Request.builder(projectRule.project, rootProjectPath, ":app:assembleDebug")
        .setMode(BuildMode.ASSEMBLE)
        .setCommandLineArguments(listOf("-PmyArgument=true"))
        .setJvmArguments(listOf("-DmyArgument=true"))
        .withEnvironmentVariables(mapOf("ENV_VAR" to "value"))
        .passParentEnvs(false)
        .setDoNotShowBuildOutputOnFailure(true)
        .waitForCompletion()
        .setListener(listener)
        .build()

    val copiedRequest = originalRequest.copyRequest()

    assertThat(copiedRequest).isNotSameAs(originalRequest)
    // Task ID should be different
    assertThat(copiedRequest.taskId).isNotEqualTo(originalRequest.taskId)
    // Everything else should be same
    assertThat(copiedRequest.gradleTasks).isEqualTo(originalRequest.gradleTasks)
    assertThat(copiedRequest.mode).isEqualTo(originalRequest.mode)
    assertThat(copiedRequest.commandLineArguments).isEqualTo(originalRequest.commandLineArguments)
    assertThat(copiedRequest.jvmArguments).isEqualTo(originalRequest.jvmArguments)
    assertThat(copiedRequest.env).isEqualTo(originalRequest.env)
    assertThat(copiedRequest.isPassParentEnvs).isEqualTo(originalRequest.isPassParentEnvs)
    assertThat(copiedRequest.doNotShowBuildOutputOnFailure).isEqualTo(originalRequest.doNotShowBuildOutputOnFailure)
    assertThat(copiedRequest.isWaitForCompletion).isEqualTo(originalRequest.isWaitForCompletion)
    assertThat(copiedRequest.executionEnvironment).isEqualTo(originalRequest.executionEnvironment)
    assertThat(copiedRequest.listener).isEqualTo(originalRequest.listener)
    // Produced execution settings should be the same
    assertThat(copiedRequest.toExecutionSettings()).isEqualTo(originalRequest.toExecutionSettings())
  }

  @Test
  fun requestFromExecutionSettings() {
    val originalSettings =
      GradleProjectSystemUtil.getOrCreateGradleExecutionSettings(projectRule.project).apply {
        this.tasks = listOf(":app:assembleDebug")
        this.withVmOptions(listOf("-DmyVmOption_0"))
          .withArguments(listOf("-PmyArgument_0=true"))
          .withEnvironmentVariables(mapOf("ENV_VAR_0" to "value"))
          .passParentEnvs(false)
        @Suppress("DEPRECATION") this.putUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE, true)
      }

    val taskId =
      ExternalSystemTaskId.create(GradleProjectSystemUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)

    val request = GradleBuildInvoker.Request.fromExecutionSettings(projectRule.project, taskId, null, rootProjectPath, originalSettings)

    // Change created settings to make sure it has no effect on existing request
    val settings1 = request.toExecutionSettings()
    settings1.withArguments(listOf("-PmyArgument_1=true"))
    settings1.withVmOption("-DmyVmOption_1")
    settings1.withEnvironmentVariables(mapOf("ENV_VAR_1" to "value"))
    @Suppress("DEPRECATION") settings1.putUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE, false)

    // Change original settings, to make sure it has no more effect on created request
    originalSettings.withArguments(listOf("-PmyArgument_2=true"))
    originalSettings.withVmOption("-DmyVmOption_2")
    originalSettings.withEnvironmentVariables(mapOf("ENV_VAR_2" to "value"))
    @Suppress("DEPRECATION") originalSettings.putUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE, false)

    // Check that values are coming from the provided original settings object
    assertThat(request.gradleTasks).containsExactly(":app:assembleDebug")
    assertThat(request.mode).isNull()
    assertThat(request.commandLineArguments).containsExactly("-PmyArgument_0=true")
    assertThat(request.jvmArguments).containsExactly("-DmyVmOption_0")
    assertThat(request.env).isEqualTo(mapOf("ENV_VAR_0" to "value"))
    assertThat(request.isPassParentEnvs).isFalse()
    assertThat(request.doNotShowBuildOutputOnFailure).isTrue()
    assertThat(request.isWaitForCompletion).isFalse()
    assertThat(request.executionEnvironment).isNull()
    assertThat(request.listener).isNull()

    // Compare all settings objects
    val settings2 = request.toExecutionSettings()
    assertThat(originalSettings).isNotEqualTo(settings2)
    assertThat(originalSettings).isNotEqualTo(settings1)
    assertThat(settings1).isNotEqualTo(settings2)
    settings2.let {
      assertThat(it.tasks).containsExactly(":app:assembleDebug")
      assertThat(it.arguments).containsExactly("-PmyArgument_0=true")
      assertThat(it.jvmArguments).containsExactly("-DmyVmOption_0")
      assertThat(it.env).isEqualTo(mapOf("ENV_VAR_0" to "value"))
      assertThat(it.isPassParentEnvs).isFalse()
      @Suppress("DEPRECATION") assertThat(it.getUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE)).isTrue()
    }
  }
}
