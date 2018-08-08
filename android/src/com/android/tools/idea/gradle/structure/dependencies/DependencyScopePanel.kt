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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo

class DependencyScopePanel(module: PsModule) : AbstractDependencyScopesPanel() {

  private val instructions: String =
    """Assign a scope to the new dependency by selecting the configurations below.<br/><a
      |href='https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html'>Open Documentation</a>"""

  val configurations = module.getConfigurations()
  val comboBox = ComboBox<String>().apply {
    configurations.forEach { addItem(it) }
  }

  init {
    comboBox.selectedItem = configurations.firstOrNull()
    setUpContents(comboBox, instructions)
  }

  override fun validateInput(): ValidationInfo? = when {
    (comboBox.selectedItem as? String).isNullOrEmpty() -> ValidationInfo("Please select at least one configuration", comboBox)
    else -> null
  }

  override val selectedScopeName: String get() = comboBox.selectedItem as String

  override fun dispose() {}
}
