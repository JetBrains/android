/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2.support

import com.android.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.usageView.UsageInfo
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

/**
 * [RenameProcessor] for id attributes to be used with the properties panel.
 *
 * Perform exact replacement only, no comment/text changes since it is potentially non-interactive.
 */
class NeleIdRenameProcessor(
  project: Project,
  attribute: XmlAttributeValue,
  newValue: String
) : RenameProcessor(project, ValueResourceElementWrapper(attribute), newValue, false, false) {

  // TODO move this static field to a PropertiesComponent setting (need a UI to reset)
  enum class RefactoringChoice { ASK, NO, YES, PREVIEW, CANCEL }

  init {
    isPreviewUsages = true
  }

  override fun previewRefactoring(usages: Array<out UsageInfo>) {
    if (usages.isEmpty()) {
      execute(usages)  // Change the id without asking, there are no references
    }
    else {
      var choice = choiceForNextRename
      if (choice == RefactoringChoice.ASK) {
        choice = dialogProvider(myProject)
      }

      when (choice) {
        RefactoringChoice.YES -> execute(usages)                      // Perform rename without preview
        RefactoringChoice.PREVIEW -> super.previewRefactoring(usages) // Perform rename with preview
        RefactoringChoice.CANCEL -> {}                                // Don't change the id property at all
        else -> execute(UsageInfo.EMPTY_ARRAY)                        // Change the id but ignore all usages!
      }
    }
  }

  companion object {
    @JvmStatic
    @VisibleForTesting
    var choiceForNextRename = RefactoringChoice.ASK

    @JvmStatic
    @TestOnly
    var dialogProvider: (Project) -> RefactoringChoice = { showRenameDialog(it) }

    private const val NO_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE
    private const val PREVIEW_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 1

    private fun showRenameDialog(project: Project): RefactoringChoice {
      val doNotAsk = CheckBox("Don't ask again during this session?")

      val actions = listOf<Action>(
        ButtonAction("No", NO_EXIT_CODE),
        ButtonAction("Preview", PREVIEW_EXIT_CODE),
        ButtonAction("Cancel", CANCEL_EXIT_CODE),
        ButtonAction("Yes", OK_EXIT_CODE)
      )

      val dialog = dialog(
        title = "Update Usages?",
        panel = panel {
          noteRow("Update usages as well?\n" +
                  "This will update all XML references and Java R field references.")
          row { doNotAsk() }
        },
        focusedComponent = null,
        project = project,
        createActions = { actions })
      dialog.show()

      val choice = when (dialog.exitCode) {
        CANCEL_EXIT_CODE -> RefactoringChoice.CANCEL
        NO_EXIT_CODE -> RefactoringChoice.NO
        PREVIEW_EXIT_CODE -> RefactoringChoice.PREVIEW
        else -> RefactoringChoice.YES
      }

      if (doNotAsk.isSelected && choice != RefactoringChoice.CANCEL) {
        choiceForNextRename = choice
      }
      return choice
    }
  }

  private class ButtonAction(text: String, private val exitCode: Int) : AbstractAction(text) {
    init {
      putValue(DialogWrapper.DEFAULT_ACTION, exitCode == OK_EXIT_CODE)
    }

    override fun actionPerformed(event: ActionEvent) {
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(exitCode)
    }
  }
}
