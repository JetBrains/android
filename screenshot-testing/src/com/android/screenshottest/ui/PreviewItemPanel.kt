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

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.util.ScreenshotTestUtils
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotViewType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ImageLoader
import com.intellij.util.concurrency.AppExecutorUtil
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
import javax.swing.JPanel

// Limits the maximum dimension (width or height) of the preview thumbnail
// to ensure it fits within the list item layout without distorting the UI.
private const val MAX_IMAGE_SIZE = 200

/**
 * A UI panel that displays a single screenshot test preview image.
 */
class PreviewItemPanel(
  val previewData: PreviewDetails,
  private val showDetails: Boolean = true,
  private val logger: Logger = Logger.getInstance(PreviewItemPanel::class.java)
) : JPanel() {
  private val imagePanel: ImagePanel
  var isLoadedSuccessfully: Boolean = false
    private set

  private val _sourceImageToCopy = mutableMapOf<String, String>()
  val sourceImageToCopy: Map<String, String> get() = _sourceImageToCopy

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

    if (showDetails) {
      val detailsPanel =
        JPanel().apply {
          layout = BoxLayout(this, BoxLayout.Y_AXIS)
          border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        }

      val matchLabel = createMatchPercentageLabel(previewData)
      val previewNameLabel =
        JBLabel(previewData.previewName).apply { alignmentX = LEFT_ALIGNMENT }
      detailsPanel.add(matchLabel)
      detailsPanel.add(previewNameLabel)
      // TODO: Add Composable link

      c.gridy = 1
      add(detailsPanel, c)
    }
  }

  private fun createMatchPercentageLabel(previewData: PreviewDetails): JPanel {
    val diffDouble = previewData.diffPercent?.toDoubleOrNull()
    val matchPercentage = ScreenshotTestUtils.calculateMatchPercentage(diffDouble)
    val percentageText = matchPercentage ?: DEFAULT_MATCH_PERCENTAGE

    val color = if (previewData.testResult == AndroidTestCaseResult.PASSED) {
      JBColor.GREEN.darker()
    } else {
      JBColor.RED
    }

    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT

      add(JBLabel("Match: "))
      add(JBLabel(percentageText).apply {
        foreground = color
        font = font.deriveFont(Font.BOLD)
      })
    }
  }

  fun showError(message: String) {
    ApplicationManager.getApplication().invokeLater {
      isLoadedSuccessfully = false
      imagePanel.showText(message)
    }
  }

  private fun showPlaceholder(message: String, color: JBColor) {
    ApplicationManager.getApplication().invokeLater {
      imagePanel.showText(message, color)
    }
  }

  fun showImageForView(viewType: ScreenshotViewType) {
    when (viewType) {
      ScreenshotViewType.ALL -> {
      }
      ScreenshotViewType.NEW -> {
        previewData.srcImagePath?.let { loadImage(it, previewData.testId) } ?: showError(NO_NEW_IMAGE_TEXT)
      }
      ScreenshotViewType.DIFF -> {
        val diffPath = previewData.diffImagePath
        if (diffPath != null && File(diffPath).exists()) {
          loadImage(diffPath, previewData.testId)
        } else {
          if (previewData.testResult == AndroidTestCaseResult.PASSED) {
            showPlaceholder(NO_DIFFERENCE_TEXT, JBColor.GREEN)
          } else {
            showPlaceholder(NO_DIFF_IMAGE_TEXT, JBColor.RED)
          }
        }
      }
      ScreenshotViewType.REFERENCE -> {
        val refPath = previewData.destImagePath
        if (refPath != null && File(refPath).exists()) {
          loadImage(refPath, previewData.testId)
        } else {
          showPlaceholder(NO_REF_IMAGE_TEXT, JBColor.RED)
        }
      }
    }
  }

  fun loadImage(newPath: String, testId: String) {
    val simpleClassName = testId.split('.', limit = 2).first()

    if (_sourceImageToCopy.isEmpty()) {
      previewData.srcImagePath?.let { _sourceImageToCopy[it] = simpleClassName }
    }

    AppExecutorUtil.getAppExecutorService().submit {
      val image = createImageIcon(newPath)

      ApplicationManager.getApplication().invokeLater {
        if (image != null) {
          imagePanel.setImage(image)

          // The layout is now independent, so we just need to trigger a re-layout.
          revalidate()
          repaint()

          isLoadedSuccessfully = true
        } else {
          logger.error("Couldn't load image from path: $newPath")
          showError(COULD_NOT_LOAD_IMAGE_TEXT)
        }
      }
    }
  }

  private fun createImageIcon(path: String): JBImageIcon? {
    val ioFile = File(path)
    if (!ioFile.exists()) {
      logger.warn("Image file not found. Path: $path")
      return null
    }
    if (ioFile.length() == 0L) {
      logger.warn("Image file is empty. Path: $path")
      return null
    }
    return try {
      val image = ImageLoader.loadFromBytes(ioFile.readBytes())
      if (image == null) {
        logger.warn("ImageLoader failed to parse image data from path: $path")
        return null
      }

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
      logger.error("IOException occurred while loading image from path: $path", e)
      null
    }
  }

  /**
   * A self-contained panel that handles its own sizing and rendering to prevent distortion.
   */
  private class ImagePanel : JPanel(GridBagLayout()) {
    private var image: JBImageIcon? = null
    private val loadingIcon = AsyncProcessIcon(WAITING_FOR_IMAGE_TEXT)
    private val initialSize = Dimension(200, 200)

    init {
      // Set an initial fixed size for the loading state.
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

    fun showText(message: String, color: JBColor = JBColor.RED) {
      this.image = null
      removeAll()
      add(JBLabel(message).apply { foreground = color })

      // Reset to the initial size to ensure the placeholder text is not clipped.
      preferredSize = initialSize
      maximumSize = initialSize

      revalidate()
      repaint()
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