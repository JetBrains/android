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
import com.intellij.openapi.ui.ValidationInfo

class DependencyScopePanel(module: PsModule, importantFor: PsModule.ImportantFor?) : AbstractDependencyScopesPanel() {

  val configurations = module.getConfigurations().toSet()
  private val comboBox = createQuickSearchComboBox(module.parent.ideProject, module.getConfigurations(), module.getConfigurations(importantFor))
    .apply { name = "configuration" }

  init {
    setUpContents(comboBox, INSTRUCTIONS)
  }

  override fun validateInput(): ValidationInfo? = when {
    comboBox.text.isNullOrEmpty() -> ValidationInfo("Please select at least one configuration", comboBox)
    !configurations.contains(comboBox.text) ->
      ValidationInfo("'${comboBox.text}' is not a valid configuration name", comboBox)
    else -> null
  }

  override val selectedScopeName: String get() = comboBox.text
  override fun dispose() {}
}

private const val INSTRUCTIONS =
    """Assign your dependency to a configuration by selecting one of the configurations below.<br/><a
      |href='https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html'>Open Documentation</a>"""

