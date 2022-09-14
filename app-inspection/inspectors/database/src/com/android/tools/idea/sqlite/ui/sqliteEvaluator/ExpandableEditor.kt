/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator

import com.intellij.ui.EditorTextField
import com.intellij.ui.Expandable
import com.intellij.ui.ExpandableEditorSupport

internal class ExpandableEditor(private val collapsedEditor: EditorTextField) : Expandable {

  private val support = MyEditorTextSupport(collapsedEditor)
  private val expandedEditor = support.expandedEditorTextField

  internal val activeEditor get() = if (isExpanded) expandedEditor else collapsedEditor

  override fun collapse() {
    support.collapse()
  }

  override fun expand() {
    support.expand()
  }

  override fun isExpanded(): Boolean {
    return support.isExpanded
  }
}

/**
 * This extension of [ExpandableEditorSupport] always uses the same instance of [EditorTextField] for the expanded editor.
 * This instance is made available to clients of this class. This allows us to keep the collapsed and expanded editors in sync.
 * eg. by setting the document on both when necessary, setting keyboard shortcuts on both etc.
 */
private class MyEditorTextSupport(editor: EditorTextField) : ExpandableEditorSupport(editor) {
  internal val expandedEditorTextField = EditorTextField(editor.project, editor.fileType)

  override fun createPopupEditor(field: EditorTextField, text: String): EditorTextField {
    expandedEditorTextField.text = text
    return expandedEditorTextField
  }
}