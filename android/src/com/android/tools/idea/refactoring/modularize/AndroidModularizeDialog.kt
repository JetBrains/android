/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.CollectionComboBoxModel
import java.awt.BorderLayout
import java.util.Locale
import javax.swing.ComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class AndroidModularizeDialog(
  project: Project,
  private val targetModules: List<Module>,
  private val processor: AndroidModularizeProcessor
) : RefactoringDialog(project, true) {

  private lateinit var myModuleCombo: ComboBox<Module>

  init {
    title = "Modularize"
    init()
  }

  override fun doAction() {
    processor.setTargetModule(myModuleCombo.selectedItem as Module)
    processor.setPreviewUsages(isPreviewUsages)
    close(OK_EXIT_CODE)
    processor.run()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())

    panel.add(
      JLabel(String.format(Locale.US, "Move %1\$d classes and %2\$d resources to:", processor.classesCount, processor.resourcesCount)),
      BorderLayout.NORTH)

    val model: ComboBoxModel<Module> = CollectionComboBoxModel(targetModules)
    myModuleCombo = ComboBox(model)
    panel.add(myModuleCombo, BorderLayout.CENTER)
    return panel
  }

}