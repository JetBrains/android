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
package com.android.tools.idea.common.actions

import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.ide.CopyPasteManagerEx
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage

private const val IMAGE_COPIED_ID = "Image copied"

private class BufferedImageTransferable(val image: BufferedImage) : Transferable {
  override fun getTransferData(flavor: DataFlavor): BufferedImage =
    when (flavor) {
      DataFlavor.imageFlavor -> image
      else -> throw UnsupportedFlavorException(flavor)
    }

  override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
    transferDataFlavors.contains(flavor)
}

/** [AnAction] that copies the result image from the given [LayoutlibSceneManager]. */
class CopyResultImageAction(
  private val sceneManagerProvider: () -> LayoutlibSceneManager?,
  title: String = "Copy Image",
  private val actionCompleteText: String = "Image copied"
) : AnAction(title) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = sceneManagerProvider()?.renderResult != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val resultImage = sceneManagerProvider()?.renderResult?.renderedImage?.copy ?: return
    CopyPasteManagerEx.getInstance().setContents(BufferedImageTransferable(resultImage))

    e.project?.let {
      Notification(IMAGE_COPIED_ID, actionCompleteText, "", NotificationType.INFORMATION)
        .notify(e.project)
    }
  }
}
