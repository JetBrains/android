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

import androidx.compose.ui.awt.ComposePanel
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotAttributesView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import org.intellij.images.editor.impl.ImageEditorManagerImpl

private const val MULTIPLE_PREVIEWS_PANEL = "MULTIPLE_PREVIEWS_PANEL"
private const val SINGLE_PREVIEW_PANEL = "SINGLE_PREVIEW_PANEL"

/**
 * A panel that displays detailed views of screenshot previews.
 * It groups previews by their method name and displays them in horizontal scroll panes.
 * This will be the place to add more metadata about the screenshots in the future.
 */
class PreviewDetailsPanel : JPanel(CardLayout()) {

  private val screenshotAttributesView = ScreenshotAttributesView()
  private val multiplePreviewsPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }
  private val singlePreviewPanel = JPanel(BorderLayout())

  init {
    add(multiplePreviewsPanel, MULTIPLE_PREVIEWS_PANEL)
    add(singlePreviewPanel, SINGLE_PREVIEW_PANEL)
  }

  /**
   * Clears the current content and displays a new set of previews.
   *
   * @param previewsToShow The list of [PreviewDetails] to display.
   * @param imagePanelMap A map to retrieve the corresponding [PreviewItemPanel] for each preview.
   * @param viewType The current view type (New, Diff, Reference) to show for each preview.
   */
  fun displayPreviews(
    previewsToShow: List<PreviewDetails>,
    imagePanelMap: Map<String, PreviewItemPanel>,
    viewType: UpdateReferenceImagesDialog.ScreenshotViewType,
    previewToolbar: ComposePanel?
  ) {
    val cardLayout = layout as CardLayout
    if (previewsToShow.size == 1 && previewToolbar != null) {
      displaySinglePreviewDetails(previewsToShow.first(), viewType, previewToolbar)
      cardLayout.show(this, SINGLE_PREVIEW_PANEL)
    } else {
      displayMultiplePreviews(previewsToShow, imagePanelMap, viewType)
      cardLayout.show(this, MULTIPLE_PREVIEWS_PANEL)
    }
  }

  /**
   * Displays a detailed view for a single screenshot preview.
   */
  private fun displaySinglePreviewDetails(
    previewData: PreviewDetails,
    viewType: UpdateReferenceImagesDialog.ScreenshotViewType,
    previewToolbar: ComposePanel
  ) {
    singlePreviewPanel.removeAll()
    val topContainerPanel = JPanel(BorderLayout())
    val titleLabel = JBLabel(previewData.previewName).apply {
      font = font.deriveFont(Font.BOLD, font.size + 4f)
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    topContainerPanel.add(titleLabel, BorderLayout.NORTH)

    if (viewType == UpdateReferenceImagesDialog.ScreenshotViewType.ALL) {
      val allImagesPanel = JPanel(GridLayout(1, 3, 10, 0))
      val imagePaths = listOf(
        previewData.srcImagePath,
        previewData.diffImagePath,
        previewData.destImagePath
      )
      imagePaths.forEach { path ->
        val panel = createImageViewerPanel(path)
        allImagesPanel.add(panel)
      }
      topContainerPanel.add(allImagesPanel, BorderLayout.CENTER)
    } else {
      val imageContainer = JPanel(BorderLayout())
      topContainerPanel.add(imageContainer, BorderLayout.CENTER)
      val imagePath = when(viewType) {
        UpdateReferenceImagesDialog.ScreenshotViewType.NEW -> previewData.srcImagePath
        UpdateReferenceImagesDialog.ScreenshotViewType.DIFF -> previewData.diffImagePath
        UpdateReferenceImagesDialog.ScreenshotViewType.REFERENCE -> previewData.destImagePath
        else -> null
      }

      if (imagePath != null && File(imagePath).exists()) {
        ApplicationManager.getApplication().executeOnPooledThread {
          val image = ImageIO.read(File(imagePath))
          ApplicationManager.getApplication().invokeLater {
            val editor = ImageEditorManagerImpl.createImageEditorUI(image)
            imageContainer.add(editor, BorderLayout.CENTER)
            imageContainer.revalidate()
            imageContainer.repaint()
          }
        }
      } else {
        imageContainer.add(JBLabel("Image not available.", JBLabel.CENTER), BorderLayout.CENTER)
      }
    }

    topContainerPanel.add(previewToolbar, BorderLayout.SOUTH)

    val detailsPanel = screenshotAttributesView.getComponent()
    updateScreenshotAttributesView(previewData)

    val splitter = JBSplitter(true, 0.5f)
    splitter.firstComponent = topContainerPanel
    splitter.secondComponent = detailsPanel
    singlePreviewPanel.add(splitter, BorderLayout.CENTER)
    singlePreviewPanel.revalidate()
    singlePreviewPanel.repaint()
  }

  private fun createImageViewerPanel(imagePath: String?): JComponent {
    val panel = JPanel(BorderLayout())
    if (imagePath != null && File(imagePath).exists()) {
      ApplicationManager.getApplication().executeOnPooledThread {
        val image = ImageIO.read(File(imagePath))
        ApplicationManager.getApplication().invokeLater {
          val editor = ImageEditorManagerImpl.createImageEditorUI(image)
          panel.add(editor, BorderLayout.CENTER)
          panel.revalidate()
          panel.repaint()
        }
      }
    } else {
      panel.add(JBLabel("Image not available.", JBLabel.CENTER), BorderLayout.CENTER)
    }
    return panel
  }

  private fun updateScreenshotAttributesView(previewData: PreviewDetails) {
    val diffPercent = previewData.diffPercent?.toDoubleOrNull()
    screenshotAttributesView.updateData(
      refImagePath = previewData.destImagePath,
      newImagePath = previewData.srcImagePath,
      testMethodName = previewData.methodName,
      testClassName = previewData.className,
      result = previewData.testResult,
      diffPercent = diffPercent
    )
  }

  /**
   * Displays multiple screenshot previews, grouped by function, in a horizontal scrolling view.
   */
  private fun displayMultiplePreviews(
    previewsToShow: List<PreviewDetails>,
    imagePanelMap: Map<String, PreviewItemPanel>,
    viewType: UpdateReferenceImagesDialog.ScreenshotViewType
  ) {
    multiplePreviewsPanel.removeAll()
    val previewsByFunction = previewsToShow.groupBy { it.methodName }
    previewsByFunction.forEach { (function, previews) ->
      val functionNameLabel =
        JBLabel(function ?: "Unnamed Function").apply {
          font = font.deriveFont(Font.BOLD, font.size + 2f)
          border = BorderFactory.createEmptyBorder(15, 5, 5, 5)
          alignmentX = JComponent.LEFT_ALIGNMENT
        }
      multiplePreviewsPanel.add(functionNameLabel)

      // Use FlowLayout to ensure components are left-aligned and not stretched.
      val horizontalPreviewsPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
          border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
        }

      previews.forEach { previewData ->
        imagePanelMap[previewData.testId]?.let { panel ->
          panel.showImageForView(viewType)
          horizontalPreviewsPanel.add(panel)
        }
      }

      val horizontalScrollPane =
        JBScrollPane(horizontalPreviewsPanel).apply {
          horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
          verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
          border = null
          alignmentX = JComponent.LEFT_ALIGNMENT
        }
      multiplePreviewsPanel.add(horizontalScrollPane)
    }
    multiplePreviewsPanel.revalidate()
    multiplePreviewsPanel.repaint()
  }
}