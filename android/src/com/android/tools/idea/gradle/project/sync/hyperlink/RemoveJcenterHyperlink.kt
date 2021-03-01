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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.issues.processor.RemoveJcenterProcessor
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Quickfix to remove jcenter from project repositories. This can be applied when jcenter method is found in any of the following places:
 *   - Project's build.gradle buildScript.repositories block
 *   - Project's settings.gradle dependencyResolutionManagement.repositories block
 *   - Affected modules build.gradle repositories block
 */
class RemoveJcenterHyperlink(val project: Project, val affectedModules: List<Module>) : NotificationHyperlink(
  "remove.jcenter.hyperlink",
  "Remove JCenter from repositories"
) {
  companion object {
    fun canBeApplied(project: Project, affectedModules: List<Module>) : Boolean {
      // There is no build model, quickfix won't be able to remove the repository
      val projectBuildModel = ProjectBuildModel.getOrLog(project) ?: return false

      // Check project
      val buildModel = projectBuildModel.projectBuildModel
      if (buildModel != null) {
        if (buildModel.buildscript().repositories().containsMethodCall("jcenter")) {
          return true
        }
      }
      // Settings.gradle
      val settingsModel = projectBuildModel.projectSettingsModel
      if (settingsModel != null) {
        if (settingsModel.dependencyResolutionManagement().repositories().containsMethodCall("jcenter")) {
          return true
        }
      }
      // Modules' build.gradle
      for (module in affectedModules) {
        val moduleModel = projectBuildModel.getModuleBuildModel(module) ?: continue
        if (moduleModel.repositories().containsMethodCall("jcenter")) {
          return true
        }
      }

      return false
    }
  }

  override fun execute(project: Project) {
    applyFix(project, RemoveJcenterProcessor(project, affectedModules))
  }

  @VisibleForTesting
  fun applyFix(project: Project, processor: RemoveJcenterProcessor) {
    if (!canBeApplied(project, affectedModules)) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        Messages.showWarningDialog(project, "Could not identify where to apply this fix", "Remove JCenter Quickfix")
      }
      return
    }
    processor.setPreviewUsages(true)
    processor.run()
  }
}
