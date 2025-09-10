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

import com.android.screenshottest.ScreenshotTestBuildSystemAdapter
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Handles the execution of the Gradle screenshot test task.
 */
class ScreenshotTestRunner(private val project: Project, private val module: Module) {

  fun run(testClassFqns: Set<String>, callback: TaskCallback) {
    val projectSystem = ScreenshotTestBuildSystemAdapter.EP_NAME.extensionList.firstOrNull() ?: return
    val settings = projectSystem.createScreenshotTaskSettings(module, "validate", testClassFqns) ?: return

    ExternalSystemUtil.runTask(
      settings,
      DefaultRunExecutor.EXECUTOR_ID,
      project,
      projectSystem.getSystemId(),
      callback,
      ProgressExecutionMode.IN_BACKGROUND_ASYNC,
      false // Do not activate the run window.
    )
  }
}