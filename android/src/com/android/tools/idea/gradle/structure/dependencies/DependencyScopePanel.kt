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
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorComboBox
import com.intellij.ui.LanguageTextField
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.util.textCompletion.TextCompletionUtil

class DependencyScopePanel(module: PsModule) : AbstractDependencyScopesPanel() {

  val configurations = module.getConfigurations().toSet()
  val comboBox = createComboBox(module)

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
    """Assign a scope to the new dependency by selecting the configurations below.<br/><a
      |href='https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html'>Open Documentation</a>"""

private fun createComboBox(module: PsModule): EditorComboBox {
  val configurations = module.getConfigurations()
  val completionProvider = object : TextFieldWithAutoCompletion.StringsCompletionProvider(configurations, null) {
    override fun createPrefixMatcher(prefix: String): PrefixMatcher = CamelHumpMatcher(prefix)
  }
  val documentCreator = TextCompletionUtil.DocumentWithCompletionCreator(completionProvider, true)
  val initialSelection = configurations.firstOrNull()
  val document = LanguageTextField.createDocument(initialSelection, PlainTextLanguage.INSTANCE, module.parent.ideProject, documentCreator)
  return EditorComboBox(document, module.parent.ideProject, StdFileTypes.PLAIN_TEXT)
    .apply { configurations.forEach { addItem(it) } }
}