/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.dependencies

import com.android.tools.idea.gradle.structure.configurables.ui.ArtifactRepositorySearchForm
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener
import com.android.tools.idea.gradle.structure.configurables.ui.properties.renderTo
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.Disposable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class LibraryDependenciesForm (module: PsModule) : LibraryDependenciesFormUi(), Disposable {
  private val searchForm: ArtifactRepositorySearchForm
  private var selected: ParsedValue<String> = ParsedValue.NotSet

  val preferredFocusedComponent: JComponent get() = searchForm.preferredFocusedComponent
  val panel: JPanel get() = myMainPanel
  val selectedLibrary: ParsedValue<String> get() = selected
  val searchErrors: List<Exception> get() = searchForm.searchErrors

  init {
    val repositories = module.getArtifactRepositories()

    searchForm = ArtifactRepositorySearchForm(module.variables, repositories)
    searchForm.add(SelectionChangeListener { selectedLibrary ->
      selected = selectedLibrary ?: ParsedValue.NotSet
      myLibraryLabel.clear()
      selectedLibrary?.renderTo(myLibraryLabel.toRenderer(), { toString() }, mapOf())
    }, this)
    mySearchPanelHost.add(searchForm.panel, BorderLayout.CENTER)
  }

  override fun dispose() = Unit
}
