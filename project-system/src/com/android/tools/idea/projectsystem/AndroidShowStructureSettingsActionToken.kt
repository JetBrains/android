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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.IdeInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable

interface AndroidShowStructureSettingsActionToken<P : AndroidProjectSystem> : Token {
  fun show(project: Project): Boolean

  companion object {
    val EP_NAME =
      ExtensionPointName<AndroidShowStructureSettingsActionToken<AndroidProjectSystem>>(
        "com.android.tools.idea.projectsystem.androidShowStructureSettingsActionToken"
      )

    fun executeAction(e: AnActionEvent): Boolean {
      val project = e.project
      return when {
        project == null && IdeInfo.getInstance().isAndroidStudio ->
          showReadOnlyIdeaProjectStructure(ProjectManager.getInstance().defaultProject)
        else -> project?.getProjectSystem()?.getTokenOrNull(EP_NAME)?.show(project) ?: false
      }
    }

    fun showReadOnlyIdeaProjectStructure(project: Project): Boolean {
      object : SingleConfigurableEditor(project, ProjectStructureConfigurable.getInstance(project), SettingsDialog.DIMENSION_KEY) {
          override fun createActions() = arrayOf(cancelAction)

          override fun getStyle() = DialogStyle.COMPACT
        }
        .show()
      return true
    }
  }
}
