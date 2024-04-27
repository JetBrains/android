/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileTypeDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons
import java.awt.Image
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Lets the user choose an image to overlay on top of the captured view to compare the app's visual
 * against design mocks.
 */
class ToggleOverlayAction(private val renderModelProvider: () -> RenderModel) :
  AnAction(StudioIcons.LayoutInspector.Toolbar.LOAD_OVERLAY) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val renderModel = renderModelProvider()
    if (renderModel.overlay != null) {
      e.presentation.icon = StudioIcons.LayoutInspector.Toolbar.CLEAR_OVERLAY
      e.presentation.text = "Clear Overlay"
    } else {
      e.presentation.icon = StudioIcons.LayoutInspector.Toolbar.LOAD_OVERLAY
      e.presentation.text = "Load Overlay"
    }
    e.presentation.isEnabled = renderModel.isActive
  }

  override fun actionPerformed(e: AnActionEvent) {
    val renderModel = renderModelProvider()
    if (renderModel.overlay != null) {
      renderModel.overlay = null
    } else {
      loadOverlay(e)
    }
  }

  private fun loadOverlay(e: AnActionEvent) {
    // choose image
    val descriptor = FileTypeDescriptor("Choose Overlay", "svg", "png", "jpg")
    val fileChooserDialog =
      FileChooserFactory.getInstance().createFileChooser(descriptor, null, null)
    val toSelect =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(e.project?.basePath ?: "/")
    val files = fileChooserDialog.choose(null, toSelect!!)
    if (files.isEmpty()) {
      return
    }
    assert(files.size == 1)

    renderModelProvider().overlay = loadImageFile(files[0])
  }

  private fun loadImageFile(file: VirtualFile): Image? {
    return try {
      ImageIO.read(file.inputStream)
    } catch (e: IOException) {
      Messages.showErrorDialog(
        "Failed to read image from \"" + file.name + "\" Error: " + e.message,
        "Error"
      )
      return null
    }
  }
}
