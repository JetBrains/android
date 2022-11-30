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
package com.android.tools.idea.common.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel


/**
 * A basic implementation of FileEditor interface for design editor. Useful in case most of the methods return obvious values or have no-op
 * implementations
 */
open class DesignFileEditor(private val virtualFile: VirtualFile) : FileEditor, SplitEditorPreviewNotificationHandler, UserDataHolderBase() {
  private val previewPanel = JPanel(BorderLayout())

  override fun getComponent(): JComponent = previewPanel
  override fun getPreferredFocusedComponent(): JComponent = previewPanel
  override fun getName() = "Base File Editor"
  override fun setState(state: FileEditorState) {}
  override fun dispose() {}
  override fun isValid() = file.isValid
  override fun isModified() = false
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getFile() = virtualFile
  override fun updateNotifications() {}
}