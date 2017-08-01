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

import com.android.tools.idea.explorer.DeviceFileId
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

import javax.swing.*
import java.beans.PropertyChangeListener

class SqliteEditor(private val project: Project, private val sqliteFile: VirtualFile) : UserDataHolderBase(), FileEditor {
  private val panel: SqliteEditorPanel = SqliteEditorPanel()

  init {
    refreshPanel()
  }

  private fun refreshPanel() {
    val deviceEntry = sqliteFile.getUserData(DeviceFileId.KEY)
    panel.localPathText.text = FileUtil.toSystemDependentName(sqliteFile.path)
    panel.deviceIdText.text = deviceEntry?.deviceId ?: "N/A"
    panel.devicePathText.text = deviceEntry?.devicePath ?: "N/A"
  }

  override fun dispose() {}

  override fun getComponent(): JComponent {
    return panel.mainPanel
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return null
  }

  override fun getName(): String {
    return sqliteFile.name
  }

  override fun setState(state: FileEditorState) {
    if (state is SqliteEditorState) {
      sqliteFile.putUserData(DeviceFileId.KEY, state.deviceFileId)
      refreshPanel()
    }
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    var fileId = sqliteFile.getUserData(DeviceFileId.KEY)
    if (fileId == null) {
      fileId = DeviceFileId.UNKNOWN
    }
    return SqliteEditorState(fileId)
  }

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return sqliteFile.isValid && !project.isDisposed
  }

  override fun selectNotify() {}

  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? {
    return null
  }

  override fun getCurrentLocation(): FileEditorLocation? {
    return null
  }
}