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

import com.android.tools.idea.gradle.structure.model.PsModule
import com.intellij.execution.Platform
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import java.io.File
import javax.swing.JComponent

const val ADD_JAR_DEPENDENCY_DIALOG_TITLE = "Add Jar/Aar Dependency"

class AddJarDependencyDialog(module: PsModule) : AbstractAddDependenciesDialog(module) {

  private var jarDependenciesForm: JarDependenciesForm? = null

  init {
    title = ADD_JAR_DEPENDENCY_DIALOG_TITLE
    init()
  }

  override fun postponeValidation(): Boolean = true

  override fun addNewDependencies() {
    module.addJarDependency(jarDependenciesForm?.directoryOrFile?.takeUnless { it.isBlank() } ?: return, scopesPanel.selectedScopeName)
  }

  override fun getSplitterProportionKey(): String = "psd.add.jar.dependency.main.horizontal.splitter.proportion"

  override fun getDependencySelectionView(): JComponent {
    if (jarDependenciesForm == null) {
      jarDependenciesForm = JarDependenciesForm(module)
    }
    return jarDependenciesForm!!.panel
  }

  override fun getInstructions(): String = "Provide a path to the library file or directory to add."

  override fun getDimensionServiceKey(): String = "psd.add.jar.dependency.panel.dimension"

  override fun getPreferredFocusedComponent(): JComponent? =
    if (jarDependenciesForm != null) jarDependenciesForm!!.preferredFocusedComponent else null

  override fun dispose() {
    super.dispose()
    if (jarDependenciesForm != null) {
      Disposer.dispose(jarDependenciesForm!!)
    }
  }

  override fun doValidate(): ValidationInfo? {
    if (jarDependenciesForm?.directoryOrFile.isNullOrBlank()) {
      return ValidationInfo("Please specify the file or directory to add as dependency", jarDependenciesForm!!.preferredFocusedComponent)
    }
    return scopesPanel.validateInput()
  }

  override fun createDependencyScopesPanel(module: PsModule): AbstractDependencyScopesPanel =
    DependencyScopePanel(module, PsModule.ImportantFor.LIBRARY)
}

fun PsModule.addJarDependency(filePath: String, configurationName: String) {
  val file = File(filePath)
  val resolvedFile = rootDir?.resolve(file) ?: file
  val isDirectory = resolvedFile.exists() && resolvedFile.isDirectory ||
                    filePath.endsWith(Platform.current().fileSeparator)
  if (isDirectory)
    addJarFileTreeDependency(
      filePath, includes = listOf("*.aar", "*.jar"), excludes = listOf(), configurationName = configurationName)
  else addJarFileDependency(filePath, configurationName)
}
