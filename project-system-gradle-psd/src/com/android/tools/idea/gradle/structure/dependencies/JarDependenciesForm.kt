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
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.ui.EditorComboBox
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

internal class JarDependenciesForm(module: PsModule) : Disposable {
  val panel: JPanel = JPanel(BorderLayout())
  private val textBox: EditorComboBox
  val preferredFocusedComponent: JComponent get() = textBox
  val directoryOrFile: String get() = textBox.text

  init {
    val ideProject = module.parent.ideProject
    val filesAndDirectories =
      ModuleManager.getInstance(ideProject)
        .findModuleByName(module.name)
        ?.let {
          it.moduleContentScope
        }?.let {
          FilenameIndex.getAllFilesByExt(ideProject, "aar", it) +
          FilenameIndex.getAllFilesByExt(ideProject, "jar", it)
        }
        ?.flatMap { listOfNotNull(it.canonicalPath, it.parent.canonicalPath) }
        ?.mapNotNull { path -> module.rootDir?.let { File(path).relativeTo(it).path } }
        ?.distinct()
        ?.sorted()
      ?: listOf()
    textBox = createQuickSearchComboBox(ideProject, filesAndDirectories, filesAndDirectories)
      .also { panel.add(it) }
  }

  override fun dispose() = Unit
}
