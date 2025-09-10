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
package com.android.screenshottest.ui

import com.android.screenshottest.util.PreviewDetails
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ImageLoader
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagLayout
import java.io.File
import java.io.IOException
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A UI panel that displays a single screenshot test preview, including its image and details.
 */
class PreviewItemPanel(
  val previewData: PreviewDetails,
  private val onImageLoaded: () -> Unit,
  private val logger: Logger = Logger.getInstance(PreviewItemPanel::class.java)
) : JPanel() {
  private val imageContainer: JPanel
  private val loadingIcon: AsyncProcessIcon
  var isLoadedSuccessfully: Boolean = false
    private set
  val loadedImagePaths = mutableMapOf<String, String>() // imagePath to simpleClassName

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    loadingIcon = AsyncProcessIcon("Waiting for image...")
    imageContainer =
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(
          JPanel(GridBagLayout()).apply {
            val fixedSize = Dimension(200, 150)
            preferredSize = fixedSize
            maximumSize = fixedSize
            border = BorderFactory.createLineBorder(JBColor.border())
            add(loadingIcon)
          }
        )
      }
    val detailsPanel =
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        alignmentX = JComponent.LEFT_ALIGNMENT
      }
    val matchLabel =
      JBLabel("New").apply {
        foreground = JBColor.GREEN.darker()
        font = font.deriveFont(Font.BOLD)
        alignmentX = JComponent.LEFT_ALIGNMENT
      }
    val previewNameLabel =
      JBLabel(previewData.toString()).apply { alignmentX = JComponent.LEFT_ALIGNMENT }
    detailsPanel.add(matchLabel)
    detailsPanel.add(previewNameLabel)
    val composable = previewData.composableFunction
    if (composable != null && composable != previewData.function) {
      val navigationAction = object : AnAction("Composable: ${composable.name}") {
        override fun actionPerformed(e: AnActionEvent) {
          if (composable.canNavigate()) composable.navigate(true)
        }
      }
      val composableLink = AnActionLink(navigationAction, ActionPlaces.UNKNOWN)
      composableLink.alignmentX = JComponent.LEFT_ALIGNMENT
      detailsPanel.add(composableLink)
    }
    add(imageContainer)
    add(detailsPanel)
  }

  fun setMultipreview() {
    loadingIcon.toolTipText = "Waiting for multipreview..."
  }

  fun showError(message: String) {
    ApplicationManager.getApplication().invokeLater {
      isLoadedSuccessfully = false
      imageContainer.removeAll()
      imageContainer.add(JBLabel(message).apply { foreground = JBColor.RED })
      imageContainer.revalidate()
      imageContainer.repaint()
    }
  }

  fun loadImage(newPath: String, testId: String) {
    val simpleClassName = testId.split('.', limit = 2).first()
    loadedImagePaths[newPath] = simpleClassName

    ApplicationManager.getApplication().executeOnPooledThread {
      val image = createImageIcon(newPath)
      ApplicationManager.getApplication().invokeLater {
        if (image != null) {
          imageContainer.removeAll()
          imageContainer.add(JBLabel(image))
          imageContainer.revalidate()
          imageContainer.repaint()
          isLoadedSuccessfully = true
          onImageLoaded()
        } else {
          showError("Couldn't load image")
        }
      }
    }
  }

  private fun createImageIcon(path: String): JBImageIcon? {
    val ioFile = File(path)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
    if (virtualFile == null || virtualFile.length == 0L) {
      logger.warn("Image file not found or is empty after build: $path")
      return null
    }
    return try {
      val image = ImageLoader.loadFromBytes(virtualFile.contentsToByteArray()) ?: return null
      val scaledImage = ImageUtil.scaleImage(image, 200, 150)
      JBImageIcon(scaledImage)
    } catch (e: IOException) {
      logger.error("IOException while loading image: $path", e)
      null
    }
  }
}