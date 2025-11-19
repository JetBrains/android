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
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ImageWithToolbarPanel
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotAttributesView
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotViewType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.BoundedRangeModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener


private const val MULTIPLE_PREVIEWS_PANEL = "MULTIPLE_PREVIEWS_PANEL"
private const val SINGLE_PREVIEW_PANEL = "SINGLE_PREVIEW_PANEL"
private val LOG = Logger.getInstance(PreviewDetailsPanel::class.java)

/**
 * A panel that displays detailed views of screenshot previews.
 * It can show a single preview with extensive details or a list of previews
 * grouped by test method.
 */
class PreviewDetailsPanel : JPanel(CardLayout()) {

  private val screenshotAttributesView = ScreenshotAttributesView()
  private val multiplePreviewsPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }
  private val singlePreviewPanel = JPanel(BorderLayout())

  // Panels for the "All" view (3-way split) in single preview mode.
  private val newImagePanel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = false, showTitle = true)
  private val diffImagePanel = ImageWithToolbarPanel(ScreenshotViewType.DIFF, showToolbar = false, showTitle = true)
  private val refImagePanel = ImageWithToolbarPanel(ScreenshotViewType.REFERENCE, showToolbar = false, showTitle = true)

  private val multiViewPanels = listOf(newImagePanel, diffImagePanel, refImagePanel)

  // Panels for the individual tabbed views in single preview mode.
  private val newImagePanelSingle = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = false)
  private val diffImagePanelSingle = ImageWithToolbarPanel(ScreenshotViewType.DIFF, showToolbar = true, showTitle = false)
  private val refImagePanelSingle = ImageWithToolbarPanel(ScreenshotViewType.REFERENCE, showToolbar = true, showTitle = false)

  // Common actions for the "All" view toolbar.
  private val commonZoomInAction = object : AnAction("Zoom In", null, AllIcons.General.ZoomIn) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.zoomIn() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.canZoomIn() }
    }
  }

  private val commonZoomOutAction = object : AnAction("Zoom Out", null, AllIcons.General.ZoomOut) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.zoomOut() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.canZoomOut() }
    }
  }

  private val commonOneToOneAction = object : AnAction("1:1", "Actual Size", AllIcons.General.ActualZoom) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.setActualSize() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
    }
  }

  private val commonFitToScreenAction = object : AnAction("Fit to Screen", "Fit image to screen", AllIcons.General.FitContent) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.fitToScreen() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
    }
  }

  private val commonToggleGridViewAction = object : ToggleAction("Grid", "Toggle Grid Overlay", AllIcons.Graph.Grid) {
    override fun isSelected(e: AnActionEvent): Boolean = multiViewPanels.firstOrNull()?.isGridVisible() ?: false
    override fun setSelected(e: AnActionEvent, state: Boolean) = multiViewPanels.forEach { it.setGridVisible(state) }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
    }
  }

  private val commonToggleChessboardAction =
    object : ToggleAction("Chessboard", "Toggle Chessboard Background", IconLoader.getIcon("/org/intellij/images/icons/expui/chessboard.svg", PreviewDetailsPanel::class.java)) {
      override fun isSelected(e: AnActionEvent): Boolean = multiViewPanels.firstOrNull()?.isChessboardVisible() ?: false
      override fun setSelected(e: AnActionEvent, state: Boolean) = multiViewPanels.forEach { it.setChessboardVisible(state) }
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
      }
    }

  init {
    add(multiplePreviewsPanel, MULTIPLE_PREVIEWS_PANEL)
    add(singlePreviewPanel, SINGLE_PREVIEW_PANEL)
  }

  /**
   * Updates the panel to display a new set of previews.
   *
   * This method decides whether to show the detailed single-preview UI
   * or the multi-preview grid based on the number of previews.
   *
   * @param previewsToShow The list of [PreviewDetails] to display.
   * @param imagePanelMap A map to retrieve the corresponding [PreviewItemPanel] for each preview.
   * @param viewType The current view type (e.g., New, Diff, All) to show.
   * @param previewToolbar The shared toolbar component, visible in single-preview mode.
   */
  fun displayPreviews(
    previewsToShow: List<PreviewDetails>,
    imagePanelMap: Map<String, PreviewItemPanel>,
    viewType: ScreenshotViewType,
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
   * Configures and displays the detailed view for a single screenshot preview.
   * This view includes the image(s), metadata attributes, and a toolbar.
   */
  private fun displaySinglePreviewDetails(
    previewData: PreviewDetails,
    viewType: ScreenshotViewType,
    previewToolbar: ComposePanel
  ) {
    singlePreviewPanel.removeAll()

    val topContent = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(10, 10, 0, 10)
    }

    val titlePanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      alignmentX = JComponent.LEFT_ALIGNMENT
      val methodNameLabel = JBLabel(previewData.methodName)
      val previewNameLabel = JBLabel(previewData.previewName).apply {
        foreground = UIUtil.getLabelDisabledForeground()
      }
      add(methodNameLabel)
      add(Box.createRigidArea(Dimension(4,0)))
      add(previewNameLabel)
    }
    topContent.add(titlePanel)
    topContent.add(Box.createRigidArea(Dimension(0, 8)))

    val separator1 = JSeparator()
    separator1.alignmentX = JComponent.LEFT_ALIGNMENT
    topContent.add(separator1)
    topContent.add(Box.createRigidArea(Dimension(0, 8)))

    val viewTitleLabel = JBLabel(viewType.displayText).apply {
      alignmentX = JComponent.LEFT_ALIGNMENT
    }
    topContent.add(viewTitleLabel)
    topContent.add(Box.createRigidArea(Dimension(0, 8)))

    val separator2 = JSeparator()
    separator2.alignmentX = JComponent.LEFT_ALIGNMENT
    topContent.add(separator2)
    topContent.add(Box.createRigidArea(Dimension(0, 4)))

    // Add the appropriate image view (either the 3-way split or the tabbed single view).
    val imageDisplayPanel = if (viewType == ScreenshotViewType.ALL) {
      setupAllImagesView(previewData)
    } else {
      setupSingleImageView(previewData, viewType)
    }
    imageDisplayPanel.alignmentX = JComponent.LEFT_ALIGNMENT
    topContent.add(imageDisplayPanel)

    val topContainerPanel = JPanel(BorderLayout()).apply {
      minimumSize = Dimension(0, 200)
      add(topContent, BorderLayout.CENTER)
      add(previewToolbar, BorderLayout.SOUTH)
    }

    // Setup the bottom panel with screenshot attributes.
    val detailsPanel = screenshotAttributesView.getComponent().apply {
      minimumSize = Dimension(0, 120)
    }
    updateScreenshotAttributesView(previewData)

    // Combine the top and bottom panels in a splitter.
    val splitter = OnePixelSplitter(true, 0.65f).apply {
      firstComponent = topContainerPanel
      secondComponent = detailsPanel
    }
    singlePreviewPanel.add(splitter, BorderLayout.CENTER)
    singlePreviewPanel.revalidate()
    singlePreviewPanel.repaint()
  }

  /**
   * Sets up the side-by-side view for New, Diff, and Reference images.
   * This view has a common toolbar and synchronized scrolling.
   */
  private fun setupAllImagesView(previewData: PreviewDetails): JComponent {
    val rightSplit = OnePixelSplitter(false, 0.5f).apply {
      firstComponent = diffImagePanel
      secondComponent = refImagePanel
    }
    val mainSplit = OnePixelSplitter(false, 0.33f).apply {
      firstComponent = newImagePanel
      secondComponent = rightSplit
    }

    // Link scrollbars for a synchronized experience.
    val horizontalModels = multiViewPanels.map { it.scrollPane.horizontalScrollBar.model }
    val verticalModels = multiViewPanels.map { it.scrollPane.verticalScrollBar.model }
    val horizontalSyncListener = SyncListener(horizontalModels)
    val verticalSyncListener = SyncListener(verticalModels)
    horizontalModels.forEach { it.addChangeListener(horizontalSyncListener) }
    verticalModels.forEach { it.addChangeListener(verticalSyncListener) }

    val diffPlaceholder = if (previewData.testResult == AndroidTestCaseResult.PASSED) "No Difference" else "No Diff Image"
    loadImageAsync(previewData.srcImagePath, newImagePanel, "No New Image")
    loadImageAsync(previewData.diffImagePath, diffImagePanel, diffPlaceholder)
    loadImageAsync(previewData.destImagePath, refImagePanel, "No Reference Image")

    val allImagesPanel = JPanel(BorderLayout()).apply {
      add(createCommonToolbar().component, BorderLayout.NORTH)
      add(mainSplit, BorderLayout.CENTER)
    }
    SwingUtilities.updateComponentTreeUI(allImagesPanel)
    return allImagesPanel
  }

  /**
   * Sets up a single image view that can be switched via a [CardLayout].
   * Each view has its own dedicated toolbar.
   */
  private fun setupSingleImageView(
    previewData: PreviewDetails,
    viewType: ScreenshotViewType
  ): JComponent {
    val imageContainer = JPanel(CardLayout())
    imageContainer.add(newImagePanelSingle, ScreenshotViewType.NEW.displayText)
    imageContainer.add(diffImagePanelSingle, ScreenshotViewType.DIFF.displayText)
    imageContainer.add(refImagePanelSingle, ScreenshotViewType.REFERENCE.displayText)

    val cardLayout = imageContainer.layout as CardLayout
    val diffPlaceholder = if (previewData.testResult == AndroidTestCaseResult.PASSED) "No Difference" else "No Diff Image"

    when (viewType) {
      ScreenshotViewType.NEW ->
        loadImageAsync(previewData.srcImagePath, newImagePanelSingle, "No New Image")
      ScreenshotViewType.DIFF ->
        loadImageAsync(previewData.diffImagePath, diffImagePanelSingle, diffPlaceholder)
      ScreenshotViewType.REFERENCE ->
        loadImageAsync(previewData.destImagePath, refImagePanelSingle, "No Reference Image")
      else -> LOG.warn("Unexpected viewType in setupSingleImageView: $viewType") // Should not happen, as ALL is handled separately.
    }
    cardLayout.show(imageContainer, viewType.displayText)
    SwingUtilities.updateComponentTreeUI(imageContainer)
    return imageContainer
  }

  private fun createCommonToolbar(): ActionToolbar {
    val actionGroup = DefaultActionGroup().apply {
      add(commonToggleChessboardAction)
      add(commonToggleGridViewAction)
      addSeparator()
      add(commonZoomOutAction)
      add(commonZoomInAction)
      add(commonOneToOneAction)
      add(commonFitToScreenAction)
    }
    return ActionManager.getInstance().createActionToolbar("ScreenshotCommonToolbar", actionGroup, true).apply {
      targetComponent = this@PreviewDetailsPanel
    }
  }

  private fun loadImageAsync(filePath: String?, targetPanel: ImageWithToolbarPanel, placeholder: String) {
    targetPanel.setPlaceholder(placeholder)
    if (filePath == null) {
      targetPanel.setImage(null)
      return
    }
    AppExecutorUtil.getAppExecutorService().submit {
      val image = try {
        val file = File(filePath)
        if (file.exists()) ImageIO.read(file) else null
      } catch (e: Exception) {
        LOG.warn("Error loading screenshot image: $filePath", e)
        null // Silently fail, the placeholder text will be shown.
      }
      UIUtil.invokeLaterIfNeeded {
        targetPanel.setImage(image)
      }
    }
  }

  private fun updateScreenshotAttributesView(previewData: PreviewDetails) {
    val diffPercent = previewData.diffPercent?.toDoubleOrNull()
    val testMethodName = if (previewData.previewName.isNotBlank() && previewData.previewName != "()") {
      val params = previewData.previewName.removeSurrounding("(", ")")
      "${previewData.methodName}_($params)"
    } else {
      previewData.methodName
    }
    screenshotAttributesView.updateData(
      refImagePath = previewData.destImagePath,
      newImagePath = previewData.srcImagePath,
      testMethodName = testMethodName,
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
    viewType: ScreenshotViewType
  ) {
    multiplePreviewsPanel.removeAll()
    multiplePreviewsPanel.layout = BorderLayout()

    val contentPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    val previewsByMethod = previewsToShow.groupBy { "${it.className}.${it.methodName}" }
    previewsByMethod.forEach { (_, previews) ->
      val functionNameLabel =
        JBLabel(previews.first().methodName ?: "Unnamed Function").apply {
          font = font.deriveFont(Font.BOLD, font.size + 2f)
          border = BorderFactory.createEmptyBorder(15, 5, 5, 5)
          alignmentX = JComponent.LEFT_ALIGNMENT
        }
      contentPanel.add(functionNameLabel)

      // Use FlowLayout to align components to the left.
      val horizontalPreviewsPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
          border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
          alignmentX = JComponent.LEFT_ALIGNMENT
        }

      previews.forEach { previewData ->
        imagePanelMap[previewData.testId]?.let { panel ->
          panel.showImageForView(viewType)
          horizontalPreviewsPanel.add(panel)
        }
      }
      contentPanel.add(horizontalPreviewsPanel)
    }

    val scrollPane = JBScrollPane(contentPanel).apply {
      verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
      border = null
    }
    multiplePreviewsPanel.add(scrollPane, BorderLayout.CENTER)
    multiplePreviewsPanel.revalidate()
    multiplePreviewsPanel.repaint()
  }

  /**
   * A ChangeListener that synchronizes the scroll position of multiple BoundedRangeModels.
   * This is used to link the scrollbars of the side-by-side image panels.
   */
  private class SyncListener(private val models: List<BoundedRangeModel>) : ChangeListener {
    private var isSyncing = false
    override fun stateChanged(e: ChangeEvent) {
      if (isSyncing) return
      try {
        isSyncing = true
        val sourceModel = e.source as BoundedRangeModel
        val newValue = sourceModel.value
        models.forEach { model ->
          if (model !== sourceModel) {
            model.value = newValue
          }
        }
      } finally {
        isSyncing = false
      }
    }
  }
}
