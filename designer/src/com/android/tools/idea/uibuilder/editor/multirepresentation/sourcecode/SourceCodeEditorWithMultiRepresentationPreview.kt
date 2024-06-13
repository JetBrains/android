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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

/**
 * A [TextEditorWithMultiRepresentationPreview] where the preview part is [SourceCodePreview] and
 * therefore it allows to have several representations for a single source code file.
 */
internal class SourceCodeEditorWithMultiRepresentationPreview(
  project: Project,
  textEditor: TextEditor,
  preview: SourceCodePreview,
) :
  TextEditorWithMultiRepresentationPreview<SourceCodePreview>(
    project,
    textEditor,
    preview,
    "Source Code Editor With Preview",
  ) {
  override fun getState(
    level: FileEditorStateLevel
  ): SourceCodeEditorWithMultiRepresentationPreviewState =
    SourceCodeEditorWithMultiRepresentationPreviewState(
      super.getState(level),
      textEditor.getState(level),
      preview.getState(level),
      if (isPureTextEditor) null else layout,
    )

  override fun setState(state: FileEditorState) {
    if (state is SourceCodeEditorWithMultiRepresentationPreviewState) {
      super.setState(state.parentState)
      textEditor.setState(state.editorState)
      preview.setState(state.previewState)

      setLayoutExplicitly(state.selectedLayout)
    } else {
      super.setState(state)
    }
  }
}
