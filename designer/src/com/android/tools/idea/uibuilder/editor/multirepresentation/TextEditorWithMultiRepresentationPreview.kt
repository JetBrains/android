/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditor

/**
 * A generic [SeamlessTextEditorWithPreview] where a preview part of it is [MultiRepresentationPreview]. It keeps track of number of
 * representations in the preview part and if none switches to the pure text editor mode.
 */
open class TextEditorWithMultiRepresentationPreview<P : MultiRepresentationPreview>(textEditor: TextEditor, preview: P, editorName: String) :
  SeamlessTextEditorWithPreview<P>(textEditor, preview, editorName) {
  init {
    isPureTextEditor = preview.representationNames.isEmpty()
    preview.onRepresentationsUpdated = { isPureTextEditor = preview.representationNames.isEmpty() }
    preview.registerShortcuts(component)
  }
}
