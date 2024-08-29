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
package com.android.tools.idea.compose.preview.actions.ml

import com.android.tools.adtui.TabularLayout
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.idea.compose.preview.isMultiPreviewAnnotation
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

class GenerateComposePreviewsForFileAction :
  GenerateComposePreviewBaseAction(message("action.generate.previews.for.file")) {
  override fun isActionFlagEnabled() = StudioFlags.COMPOSE_PREVIEW_GENERATE_ALL_PREVIEWS_FILE.get()

  override fun getTargetComposableFunctions(e: AnActionEvent): List<KtNamedFunction> {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return emptyList()
    val functionsInFile = psiFile.collectDescendantsOfType<KtNamedFunction>()
    val composableFunctions = mutableListOf<KtNamedFunction>()
    functionsInFile.forEach { function ->
      if (function.annotationEntries.any { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) }) {
        composableFunctions.add(function)
      }
      if (
        function.annotationEntries.any {
          val uAnnotation = (it.toUElement() as? UAnnotation) ?: return@any false
          return@any uAnnotation.isPreviewAnnotation() || uAnnotation.isMultiPreviewAnnotation()
        }
      ) {
        // If the file has previews, return an empty list because the action should not be shown
        return@getTargetComposableFunctions emptyList()
      }
    }
    return composableFunctions
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    SelectComposablesDialog(project, getTargetComposableFunctions(e)) { composables ->
        generateComposePreviews(e) { composables }
      }
      .showAndGet()
  }

  /**
   * Dialog to list Composable functions of this file as checkboxes, so the user can select which
   * previews they want to generate.
   */
  private class SelectComposablesDialog(
    project: Project,
    private val composablesInFile: List<KtNamedFunction>,
    private val generatePreviewsForComposableFunctions: (List<KtNamedFunction>) -> Unit,
  ) : DialogWrapper(project) {

    /**
     * Maps function name to its corresponding [JBCheckBox]. It's used to determine which
     * Composables will have previews generated for.
     */
    private val checkBoxes =
      composablesInFile.associateBy({ it.name }, { JBCheckBox(it.name, true) })

    init {
      title = message("action.generate.previews.for.file.dialog.title")
      init()
    }

    override fun createCenterPanel(): JComponent {
      // [Composables][padding][Horizontal separator]
      // [Empty Panel] or [Checkboxes panel]
      val mainPanel = JPanel(TabularLayout("Fit,5px,*", "Fit,*"))

      mainPanel.add(
        JBLabel(message("action.generate.previews.for.file.dialog.composables.label")),
        TabularLayout.Constraint(0, 0),
      )
      // Centralize the separator vertically
      val separatorPanel = JPanel(TabularLayout("*", "*,Fit,*"))
      separatorPanel.add(JSeparator(), TabularLayout.Constraint(1, 0))
      mainPanel.add(separatorPanel, TabularLayout.Constraint(0, 2))

      val composablesPanel = createComposablesPanel(composablesInFile)
      mainPanel.add(composablesPanel, TabularLayout.Constraint(1, 0, 3))

      return mainPanel
    }

    override fun doOKAction() {
      // Invokes the dialog callback, passing the composable functions whose corresponding
      // checkboxes  are checked. The callback will generate a Compose Preview per checked
      // function.
      composablesInFile
        .filter { checkBoxes[it.name]?.isSelected ?: false }
        .let { generatePreviewsForComposableFunctions(it) }
      super.doOKAction()
    }

    /** Creates a panel that will list Composables in the file as checkboxes. */
    private fun createComposablesPanel(composableCandidates: List<KtNamedFunction>): JComponent {
      if (composableCandidates.isEmpty()) {
        return JBLabel(message("action.generate.previews.for.file.dialog.empty"))
      }
      // Checkboxes are horizontally aligned to the left.
      // Vertical alignment is Glue, checkboxes (n * Fit), Glue.
      val checkBoxesPanel =
        JPanel(TabularLayout("Fit,*", "*,${"Fit,".repeat(composableCandidates.size)}*"))
      // Skip the first row, as it will be a vertical glue
      var row = 1
      for (checkBox in checkBoxes) {
        checkBoxesPanel.add(checkBox.value, TabularLayout.Constraint(row++, 0))
      }
      return checkBoxesPanel
    }
  }
}
