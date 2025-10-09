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
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.io.IOException
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

// Define constraints for the image panel size.
private const val MAX_IMAGE_SIZE = 200

/**
 * A UI panel that displays a single screenshot test preview, including its image and details.
 */
class PreviewItemPanel(
  val previewData: PreviewDetails,
  private val onImageLoaded: () -> Unit,
  private val logger: Logger = Logger.getInstance(PreviewItemPanel::class.java)
) : JPanel() {
  private val imagePanel: ImagePanel
  private val detailsPanel: JPanel
  var isLoadedSuccessfully: Boolean = false
    private set
  val loadedImagePaths = mutableMapOf<String, String>() // imagePath to simpleClassName

  init {
    // Use GridBagLayout to stack components vertically without forcing them to the same width.
    layout = GridBagLayout()
    isOpaque = false

    val c = GridBagConstraints()
    c.gridx = 0
    c.anchor = GridBagConstraints.WEST // Pin components to the left.
    c.fill = GridBagConstraints.NONE   // Do not allow components to stretch.
    c.weightx = 0.0

    imagePanel = ImagePanel()
    c.gridy = 0
    add(imagePanel, c)

    detailsPanel =
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
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

    c.gridy = 1
    add(detailsPanel, c)
  }

  fun setMultipreview() {
    imagePanel.setMultipreview()
  }

  fun showError(message: String) {
    ApplicationManager.getApplication().invokeLater {
      isLoadedSuccessfully = false
      imagePanel.showError(message)
    }
  }

  fun loadImage(newPath: String, testId: String) {
    val simpleClassName = testId.split('.', limit = 2).first()
    loadedImagePaths[newPath] = simpleClassName

    ApplicationManager.getApplication().executeOnPooledThread {
      val image = createImageIcon(newPath)
      ApplicationManager.getApplication().invokeLater {
        if (image != null) {
          imagePanel.setImage(image)

          // The layout is now independent, so we just need to trigger a re-layout.
          revalidate()
          repaint()

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

      val w = image.getWidth(null)
      val h = image.getHeight(null)

      // If the image is already within bounds, no scaling is needed.
      if (w <= 0 || h <= 0 || (w <= MAX_IMAGE_SIZE && h <= MAX_IMAGE_SIZE)) {
        return JBImageIcon(image)
      }

      // Calculate new dimensions while preserving aspect ratio to fit within MAX_IMAGE_SIZE
      val newW: Int
      val newH: Int
      if (w > h) {
        newW = MAX_IMAGE_SIZE
        newH = (h.toDouble() * newW / w.toDouble()).toInt()
      } else {
        newH = MAX_IMAGE_SIZE
        newW = (w.toDouble() * newH / h.toDouble()).toInt()
      }

      // Ensure we don't get zero dimensions for very thin/short images
      val finalW = newW.coerceAtLeast(1)
      val finalH = newH.coerceAtLeast(1)

      val scaledImage = ImageUtil.scaleImage(image, finalW, finalH)
      JBImageIcon(scaledImage)
    } catch (e: IOException) {
      logger.error("IOException while loading image: $path", e)
      null
    }
  }

  /**
   * A self-contained panel that handles its own sizing and rendering to prevent distortion.
   */
  private class ImagePanel : JPanel(GridBagLayout()) {
    private var image: JBImageIcon? = null
    private val loadingIcon = AsyncProcessIcon("Waiting for image...")

    init {
      // Set an initial fixed size for the loading state.
      val initialSize = Dimension(200, 200)
      preferredSize = initialSize
      maximumSize = initialSize
      border = BorderFactory.createLineBorder(JBColor.border())
      add(loadingIcon)
    }

    fun setImage(newImage: JBImageIcon) {
      this.image = newImage
      removeAll() // Remove loading icon

      // Lock the panel's size to the image's size. This is the key to preventing distortion.
      val newSize = Dimension(newImage.iconWidth, newImage.iconHeight)
      preferredSize = newSize
      maximumSize = newSize

      revalidate()
      repaint()
    }

    fun showError(message: String) {
      removeAll()
      add(JBLabel(message).apply { foreground = JBColor.RED })
      revalidate()
      repaint()
    }

    fun setMultipreview() {
      loadingIcon.toolTipText = "Waiting for multipreview..."
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      // Manually paint the image to ensure it's centered and not scaled by the layout manager.
      image?.let {
        val x = (width - it.iconWidth) / 2
        val y = (height - it.iconHeight) / 2
        it.paintIcon(this, g, x, y)
      }
    }
  }
}