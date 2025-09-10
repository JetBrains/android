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
package com.android.screenshottest.gradle

import com.android.screenshottest.ScreenshotTestBuildSystemAdapter
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.gradleIdentityPath

/**
 * Gradle-specific implementation of [ScreenshotTestBuildSystemAdapter].
 * This class uses [GradleAndroidModel] to retrieve variant and task information.
 */
class GradleScreenshotTestBuildSystemAdapter : ScreenshotTestBuildSystemAdapter {

  override fun getSelectedVariantName(module: Module): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    val androidModel = GradleAndroidModel.get(facet) ?: return null
    return androidModel.selectedVariantName
  }

  override fun getScreenshotTestTaskName(module: Module, command: String): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    val androidModel = GradleAndroidModel.get(facet) ?: return null
    return androidModel.getGradleScreenshotTestTaskNameForSelectedVariant(command)
  }

  override fun getLinkedExternalProjectPath(module: Module): String? {
    return GradleUtil.findGradleModuleData(module)?.data?.getLinkedExternalProjectPath()
  }

  override fun getSystemId(): ProjectSystemId = GradleConstants.SYSTEM_ID

  override fun createScreenshotTaskSettings(module: Module, command: String, testClassFqns: Set<String>): ExternalSystemTaskExecutionSettings? {
    val taskName = getScreenshotTestTaskName(module, command) ?: return null
    val moduleData = GradleUtil.findGradleModuleData(module)?.data ?: return null

    val taskFullPath = moduleData.gradleIdentityPath.trimEnd(':') + ":" + taskName
    val taskArguments = testClassFqns.flatMap { listOf("--tests", it) }

    return ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = moduleData.linkedExternalProjectPath
      taskNames = listOf(taskFullPath) + taskArguments
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
  }
}