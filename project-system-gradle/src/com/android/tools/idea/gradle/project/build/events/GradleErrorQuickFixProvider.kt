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
package com.android.tools.idea.gradle.project.build.events

import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.intellij.build.events.BuildEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project

/**
 * Quick fix provider to provide quick fixes for Gradle build/sync errors.
 */
interface GradleErrorQuickFixProvider {
  fun isAvailable(): Boolean = false
  fun runQuickFix(context: GradleErrorContext, project: Project)

  /**
   * Creates a [DescribedBuildIssueQuickFix] that is provided for build outputs.
   *
   * This function analyzes a [BuildEvent] and a corresponding [ExternalSystemTaskId] to determine
   * if a quick fix can be offered to the user for a build issue.
   */
  fun createBuildIssueQuickFixFor(buildEvent: BuildEvent, taskId: ExternalSystemTaskId): DescribedBuildIssueQuickFix?

  companion object {
    val EP_NAME = ExtensionPointName.create<GradleErrorQuickFixProvider>("com.android.tools.idea.gradle.errorQuickFixProvider")

    private val gradleErrorQuickFixProviderUnavailable = object : GradleErrorQuickFixProvider {
      override fun runQuickFix(context: GradleErrorContext, project: Project) {}
      override fun createBuildIssueQuickFixFor(buildEvent: BuildEvent, taskId: ExternalSystemTaskId): DescribedBuildIssueQuickFix? = null
    }

    fun getInstance(): GradleErrorQuickFixProvider {
      return EP_NAME.extensionList.firstOrNull() ?: gradleErrorQuickFixProviderUnavailable
    }
  }
}