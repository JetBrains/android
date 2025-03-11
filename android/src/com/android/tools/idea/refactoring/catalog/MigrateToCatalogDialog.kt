/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.refactoring.catalog

import com.intellij.openapi.project.Project
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

internal class MigrateToCatalogDialog(
  project: Project,
  private val processor: MigrateToCatalogProcessor,
) : RefactoringDialog(project, true) {

  private var unifyVersions: Boolean = true
  private var groupVariables: Boolean = true

  init {
    title = "Migrate to Version Catalog"
    init()
  }

  override fun createNorthPanel(): JComponent {
    return panel {
        row {
          checkBox("Unify dependencies across modules to a single version")
            // Would prefer to use just ".bindSelected(::unifyVersions)" here, but it doesn't work
            // .bindSelected(::unifyVersions)
            .bindSelected(
              getter = { unifyVersions },
              // Doesn't seem to be called, so we use onChanged as well
              setter = { selected -> unifyVersions = selected },
            )
            .onChanged { unifyVersions = it.isSelected }
        }
        row {
          comment(
            "Rather than creating individual catalog entries for different versions of the same library, " +
              "this will pick the highest version and uniformly use this across the project"
          )
        }

        row {
          checkBox("Create a single version variable for related libraries using the same version")
            .bindSelected(::groupVariables)
            .bindSelected(
              getter = { groupVariables },
              setter = { selected -> groupVariables = selected },
            )
            .onChanged { groupVariables = it.isSelected }
        }
      }
      .withBorder(JBUI.Borders.empty())
  }

  override fun createCenterPanel(): JComponent? = null

  override fun doAction() {
    processor.unifyVersions = unifyVersions
    processor.groupVersionVariables = groupVariables
    @Suppress("UsePropertyAccessSyntax") processor.setPreviewUsages(isPreviewUsages)
    close(OK_EXIT_CODE)
    processor.run()
  }
}
