/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.sqlite

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

class SqliteEditorProvider : FileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!SqliteViewer.isFeatureEnabled) {
      return false
    }

    return file.fileType === SqliteFileType
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (!SqliteViewer.isFeatureEnabled) {
      throw IllegalStateException("SqliteViewer is not enabled, editor should not be created")
    }

    return SqliteEditor(project, file)
  }

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is SqliteEditorState) {
      state.writeState(targetElement)
    }
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    return SqliteEditorState.readState(sourceElement)
  }

  override fun getEditorTypeId(): String = SQLITE_EDITOR_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR

  companion object {
    private val SQLITE_EDITOR_ID = "android-sqlite-editor-id"
  }
}