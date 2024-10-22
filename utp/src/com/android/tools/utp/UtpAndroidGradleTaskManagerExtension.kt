/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.utp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.loadInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * Extension of GradleTaskManager to enhance processing of test results from
 * DeviceProviderInstrumentedTestTask to Android Studio with Unified Test Platform.
 */
class UtpAndroidGradleTaskManagerExtension : GradleTaskManagerExtension {

  companion object {
    /**
     * A Gradle project property to enable UTP test results reporting.
     */
    const val ENABLE_UTP_TEST_REPORT_PROPERTY: String = "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"

    private const val ANDROID_TEST_SCRIPT_NAME = "addGradleAndroidTestListener"

    private val LOG by lazy { Logger.getInstance(UtpAndroidGradleTaskManagerExtension::class.java) }
  }

  override fun configureTasks(
    projectPath: String,
    id: ExternalSystemTaskId,
    settings: GradleExecutionSettings,
    gradleVersion: GradleVersion?,
  ) {
    try {
      val initScript = loadInitScript(javaClass, "/utp/addGradleAndroidTestListener.gradle")
      settings.addInitScript(ANDROID_TEST_SCRIPT_NAME, initScript)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }
}