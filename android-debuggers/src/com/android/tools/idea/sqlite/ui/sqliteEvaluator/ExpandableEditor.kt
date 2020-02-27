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

import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.Expandable
import com.intellij.ui.ExpandableEditorSupport

internal class ExpandableEditor(internal val editor: EditorTextField) : Expandable {

  private val support = MyEditorTextSupport(editor)

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
 * This extension of [ExpandableEditorSupport] doesn't use the same document instance for both [EditorTextField]s.
 * Using the same instance causes a bug when the expanded [EditorTextField] is opened,
 * its text is correctly formatted (with expanded [FoldRegion]s) but immediately replaced with the text from the collapsed [EditorTextField].
 */
private class MyEditorTextSupport(editor: EditorTextField) : ExpandableEditorSupport(editor) {
  override fun createPopupEditor(field: EditorTextField, text: String): EditorTextField {
    val newEditorTextFiled = EditorTextField(field.project, field.fileType)
    newEditorTextFiled.text = text

    val documentManager = FileDocumentManager.getInstance()
    val schema = documentManager.getFile(field.document)?.getUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY)
    documentManager.getFile(newEditorTextFiled.document)?.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)

    return newEditorTextFiled
  }
}