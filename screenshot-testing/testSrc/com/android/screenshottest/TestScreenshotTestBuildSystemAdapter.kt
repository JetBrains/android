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
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * A shared test implementation of [ScreenshotTestBuildSystemAdapter] for use across screenshot testing unit tests.
 * It provides sensible defaults that can be overridden in the constructor.
 */
internal class TestScreenshotTestBuildSystemAdapter(
  private val project: Project,
  private val variantName: String? = "debug",
  private val taskName: String? = "validateDebugScreenshotTest",
  private val moduleName: String = "app"
) : ScreenshotTestBuildSystemAdapter {

  override fun getSelectedVariantName(module: Module): String? = variantName

  override fun getScreenshotTestTaskName(module: Module, command: String): String? = taskName

  override fun getLinkedExternalProjectPath(module: Module): String? = File(project.basePath, moduleName).path

  override fun getSystemId(): ProjectSystemId = GradleConstants.SYSTEM_ID

  override fun createScreenshotTaskSettings(
    module: Module,
    command: String,
    testClassFqns: Set<String>
  ): ExternalSystemTaskExecutionSettings? {
    val modulePath = getLinkedExternalProjectPath(module) ?: return null
    val effectiveTaskName = getScreenshotTestTaskName(module, command) ?: return null
    val taskFullPath = ":$moduleName:$effectiveTaskName"
    val taskArguments = testClassFqns.flatMap { listOf("--tests", it) }

    return ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = modulePath
      taskNames = listOf(taskFullPath) + taskArguments
      externalSystemIdString = getSystemId().id
    }
  }
}