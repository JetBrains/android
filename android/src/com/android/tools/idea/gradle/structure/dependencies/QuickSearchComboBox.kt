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

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorComboBox
import com.intellij.ui.LanguageTextField
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.util.textCompletion.TextCompletionUtil

fun createQuickSearchComboBox(
  ideProject: Project,
  choices: List<String>,
  importantChoices: List<String>
)
  : EditorComboBox {
  val completionProvider = object : TextFieldWithAutoCompletion.StringsCompletionProvider(choices, null) {
    override fun createPrefixMatcher(prefix: String): PrefixMatcher = CamelHumpMatcher(
      prefix)
  }
  val documentCreator = TextCompletionUtil.DocumentWithCompletionCreator(completionProvider, true)
  val document = LanguageTextField.createDocument("", PlainTextLanguage.INSTANCE, ideProject,
                                                  documentCreator)
  val initialSelection = importantChoices.firstOrNull()
  return EditorComboBox(document, ideProject, StdFileTypes.PLAIN_TEXT)
    .apply {
      selectedItem = initialSelection
      importantChoices.forEach { addItem(it) }
    }
}