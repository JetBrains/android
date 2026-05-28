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
package com.android.tools.idea.compose.meshgradient

import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent

class MeshGradientEditorAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = MeshGradientEditorPlaygroundDialog(project)
    dialog.show()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && StudioFlags.COMPOSE_MESH_GRADIENT_EDITOR.get()
  }
}

// JetBrains patch: DialogWrapper is frontend API in a shared module. The Android plugin is not split into frontend and backend modules, so there is no frontend module to move this into.
@Suppress("SplitModeApiUsage")
class MeshGradientEditorPlaygroundDialog(private val project: Project) : DialogWrapper(project, true) {
  private val state = MeshGeneratorState()

  init {
    title = "Mesh Gradient Editor"
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = StudioComposePanel {
      // Pass isEditingExisting = false to enable generator layouts
      MeshGradientEditorScreen(project, state, isEditingExisting = false)
    }
    panel.preferredSize = Dimension(520, 650)
    return panel
  }
}
