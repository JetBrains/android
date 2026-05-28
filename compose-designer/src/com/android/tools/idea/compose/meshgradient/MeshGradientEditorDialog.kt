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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.idea.compose.meshgradient.MeshGradientPsiManager.ParsedMesh
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.awt.Dimension
import javax.swing.JComponent
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

// JetBrains patch: DialogWrapper is frontend API in a shared module. The Android plugin is not split into frontend and backend modules, so there is no frontend module to move this into.
@Suppress("SplitModeApiUsage")
class MeshGradientEditorDialog(private val project: Project, private val file: KtFile, painterCall: KtCallExpression) :
  DialogWrapper(project, true) {

  private val psiManager = MeshGradientPsiManager(project)
  internal val state = MeshGeneratorState()
  private var parsedMesh: ParsedMesh? = null
  private val painterCallPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(painterCall)

  init {
    title = "Mesh Gradient Editor"

    runReadActionBlocking { parsedMesh = psiManager.parseMesh(painterCall) }

    parsedMesh?.let { parsed ->
      val grid =
        List(parsed.rows) { r ->
          List(parsed.cols) { c ->
            val vertex = parsed.vertices.firstOrNull { it.row == r && it.col == c }
            val offset = vertex?.offset ?: Offset(c.toFloat() / (parsed.cols - 1), r.toFloat() / (parsed.rows - 1))
            val color = vertex?.color ?: Color.White
            Pair(offset, color)
          }
        }
      state.loadMesh(parsed.rows, parsed.cols, grid)
    }

    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = StudioComposePanel { MeshGradientEditorScreen(project, state, isEditingExisting = true) }
    panel.preferredSize = Dimension(460, 530)
    return panel
  }

  public override fun doOKAction() {
    val call = painterCallPointer.element ?: return super.doOKAction()

    WriteCommandAction.runWriteCommandAction(
      project,
      "Update Mesh Gradient",
      null,
      {
        val successArgs = psiManager.updateConstructorArguments(call, state.rows, state.cols)
        val successBody = psiManager.regenerateLambdaBody(call, state.meshPoints)
        if (!successArgs || !successBody) {
          throw IllegalStateException("Failed to update mesh structure! Args success: $successArgs, Body success: $successBody")
        }
        CodeStyleManager.getInstance(project).reformat(call)
      },
      file,
    )

    super.doOKAction()
  }
}
