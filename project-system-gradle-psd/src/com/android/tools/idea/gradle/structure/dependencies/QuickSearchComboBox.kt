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
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
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
  return object : EditorComboBox(document, ideProject, PlainTextFileType.INSTANCE) {
    // we respond to documentChanged events to set the selected item immediately (see below), since we need the notion of selected item
    // and the text displayed in the combo box to stay in sync.  However, setting the selected item unconditionally sets the text to the
    // string representation of the selected item, which would (if uncaught) lead to infinite recursion.  Intercepting setText() to do
    // nothing if the text is already equal to what we would set it to prevents this infinite recursion.
    override fun setText(newText: String) {
      if (newText != text) {
        super.setText(newText)
      }
    }

    init {
      selectedItem = initialSelection
      importantChoices.forEach { addItem(it) }
      addDocumentListener(object : DocumentListener {
        // this documentChanged method keeps the selected item of the Combo in sync with the text in the editor, and has the added effect
        // of firing an actionPerformed event whenever the document is changed.  (This isn't quite the same behaviour as the JComboBox,
        // but for our purposes it is adequate).
        override fun documentChanged(event: DocumentEvent) {
          selectedItem = text
        }
      })
    }
  }
}