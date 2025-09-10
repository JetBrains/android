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
package com.android.screenshottest

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * An adapter that connects the IDE's screenshot testing features to the project's build system (e.g., Gradle).
 * It's responsible for providing the specific variant names, task names, and project paths required to run screenshot tests.
 */
interface ScreenshotTestBuildSystemAdapter {
  companion object {
    val EP_NAME = ExtensionPointName.create<ScreenshotTestBuildSystemAdapter>("com.android.screenshottest.screenshotTestProjectSystem")
  }

  /**
   * Returns the name of the currently selected build variant for the given module.
   */
  fun getSelectedVariantName(module: Module): String?

  /**
   * Constructs the name of the screenshot test task for the selected variant.
   */
  fun getScreenshotTestTaskName(module: Module, command: String): String?

  /**
   * Returns the absolute path to the root of the external project (e.g., Gradle project)
   * that the given module belongs to.
   */
  fun getLinkedExternalProjectPath(module: Module): String?

  /**
   * Returns the unique ID of the project system (e.g., Gradle's system ID).
   */
  fun getSystemId(): ProjectSystemId

  /**
   * Creates the settings required to execute a screenshot test task for the given module and test classes.
   * @return An `ExternalSystemTaskExecutionSettings` object configured for the task, or null if it cannot be created.
   */
  fun createScreenshotTaskSettings(module: Module, command: String, testClassFqns: Set<String>): ExternalSystemTaskExecutionSettings?
}