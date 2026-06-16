/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.actions

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowStructureSettingsAction
import com.intellij.ide.trustedProjects.TrustedProjects.isProjectTrusted
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

/** Displays the "Project Structure" dialog. */
class AndroidShowStructureSettingsAction : ShowStructureSettingsAction() {
  init {
    val templatePresentation = getTemplatePresentation()
    templatePresentation.setIcon(AllIcons.General.ProjectStructure)
    templatePresentation.setText(ActionsBundle.actionText("ShowProjectStructureSettings"))
    templatePresentation.setDescription(ActionsBundle.actionDescription("ShowProjectStructureSettings"))
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.getPresentation()

    val project = e.getProject()
    presentation.setEnabledAndVisible(e.getProject() != null)
    if (project == null || !isProjectTrusted(project)) {
      presentation.setEnabled(false)
    }

    super.update(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    var project = e.getProject()
    if (project == null && IdeInfo.getInstance().isAndroidStudio()) {
      project = ProjectManager.getInstance().getDefaultProject()
      showAndroidProjectStructure(project)
      return
    }

    if (project != null && project.getProjectSystem() is GradleProjectSystem) {
      showAndroidProjectStructure(project)
      return
    }

    super.actionPerformed(e)
  }
}

private fun showAndroidProjectStructure(project: Project) {
  com.android.tools.idea.structure.dialog.ProjectStructureConfigurable.getInstance(project).show()
}
