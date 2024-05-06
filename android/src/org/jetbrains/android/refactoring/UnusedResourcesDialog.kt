/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.intellij.openapi.project.Project
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.StateRestoringCheckBox
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class UnusedResourcesDialog(
  project: Project,
  private val processor: UnusedResourcesProcessor
) : RefactoringDialog(project, true) {

  private lateinit var checkBoxIncludeIds: StateRestoringCheckBox

  init {
    title = "Remove Unused Resources"
    init()
  }

  override fun createNorthPanel(): JComponent {
    val panel = JPanel(BorderLayout())

    checkBoxIncludeIds = StateRestoringCheckBox()
    checkBoxIncludeIds.setText("Delete unused @id declarations too")
    panel.add(checkBoxIncludeIds, BorderLayout.CENTER)

    return panel
  }

  override fun createCenterPanel(): JComponent? = null

  override fun doAction() {
    processor.includeIds = checkBoxIncludeIds.isSelected
    processor.setPreviewUsages(isPreviewUsages)
    close(OK_EXIT_CODE)
    processor.run()
  }
}
