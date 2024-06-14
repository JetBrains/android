/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.AddComposeCompilerGradlePluginProcessor
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Quickfix to apply the Compose Compiler Gradle plugin to modules that need it.
 */
class AddComposeCompilerGradlePluginHyperlink(
  val project: Project,
  val affectedModules: List<Module>,
  val kotlinVersion: String
) : SyncIssueNotificationHyperlink(
  "add.compose.compiler.gradle.plugin.hyperlink",
  "Add Compose Compiler Gradle plugin",
  AndroidStudioEvent.GradleSyncQuickFix.ADD_COMPOSE_COMPILER_GRADLE_PLUGIN_HYPERLINK
) {
  companion object {
    fun canBeApplied(project: Project, affectedModules: List<Module>) : Boolean {
      // If there are no affected modules, there will be nothing to do
      if (affectedModules.isEmpty()) {
        return false
      }
      // If there is no build model, quickfix won't be able to add the Gradle plugin
      return ProjectBuildModel.getOrLog(project)?.projectBuildModel != null
    }
  }

  override fun execute(project: Project) {
    applyFix(
      project,
      AddComposeCompilerGradlePluginProcessor(project, affectedModules, kotlinVersion)
    )
  }

  @VisibleForTesting
  fun applyFix(project: Project, processor: AddComposeCompilerGradlePluginProcessor) {
    if (!canBeApplied(project, affectedModules)) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        Messages.showWarningDialog(
          project,
          "Could not identify where to apply this fix",
          "Add Compose Compiler Gradle Plugin"
        )
      }
      return
    }
    processor.setPreviewUsages(true)
    processor.run()
  }
}
