/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui

import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.idea.whatsnew.assistant.WhatsNewMetricsTracker
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewAssets
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewMarkdownDocument
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.DefaultImagePainterLoader
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ImagePainterLoader
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import org.jetbrains.annotations.Nls

class WhatsNewEditor(
  val virtualFile: WhatsNewVirtualFile,
  markdownDocuments: List<WhatsNewMarkdownDocument>,
  whatsNewAssets: WhatsNewAssets,
  val project: Project,
) : UserDataHolderBase(), FileEditor {
  private val imageLoader: ImagePainterLoader = DefaultImagePainterLoader()

  private val panel: JComponent = StudioComposePanel {
    WhatsNewEditorPanel(markdownDocuments = markdownDocuments, whatsNewAssets = whatsNewAssets, imageLoader = imageLoader)
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusedComponent(): JComponent = panel

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return "What's New"
  }

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun dispose() {
    WhatsNewMetricsTracker.getInstance().close(project)
  }

  override fun getFile(): VirtualFile {
    return virtualFile
  }
}
