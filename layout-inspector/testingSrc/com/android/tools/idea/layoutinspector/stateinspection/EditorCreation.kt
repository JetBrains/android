/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.util.Disposer

/** Utility method for creating an editor with the specified [content]. */
fun AndroidProjectRule.createEditorWithContent(content: String): Editor {
  val editorFactory = EditorFactory.getInstance()
  val document = editorFactory.createDocument(content)
  val editor = editorFactory.createViewer(document, project, EditorKind.CONSOLE)
  Disposer.register(testRootDisposable) { editorFactory.releaseEditor(editor) }
  return editor
}
