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

import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

internal class UnusedResourcesDialog(
  project: Project,
  private val filterAndDescription: FilterAndDescription?,
) : RefactoringDialog(project, true) {

  class FilterAndDescription(val filter: UnusedResourcesProcessor.Filter, val description: String)

  private var centerPanel: DialogPanel? = null

  private var searchEntireProject = false
  private var includeIds = false

  init {
    title = "Remove Unused Resources"
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        if (filterAndDescription != null) {
          checkBox("Search entire project")
            .bindSelected(::searchEntireProject)
            .comment("When unchecked, ${filterAndDescription.description}.")
        } else {
          checkBox("Search entire project")
            .bindSelected(::searchEntireProject)
            .enabled(false)
            .selected(true)
            .comment("To restrict the scope, open a resource file or " +
                     "select some files/directories in the Project tool window, " +
                     "and then invoke the refactoring.")
        }
      }
      row {
        checkBox("Delete unused @id declarations too")
          .bindSelected(::includeIds)
      }
    }.also { centerPanel = it }
  }

  override fun doAction() {
    // Must call apply for "bindSelected" to work.
    // There is some logic to make this happen automatically for the center panel,
    // but this does not seem to work with RefactoringDialog.
    centerPanel?.apply()

    val filter = if (searchEntireProject) {
      null
    } else {
      filterAndDescription?.filter
    }

    val processor = UnusedResourcesProcessor(myProject, filter, includeIds)
    processor.setPreviewUsages(isPreviewUsages)
    close(OK_EXIT_CODE)
    processor.run()
  }
}
