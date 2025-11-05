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
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotAttributesView
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.BoundedRangeModel
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max
import kotlin.math.min


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
  private val newImagePanel = ImageWithToolbarPanel("New", showToolbar = false, showTitle = true)
  private val diffImagePanel = ImageWithToolbarPanel("Diff", showToolbar = false, showTitle = true)
  private val refImagePanel = ImageWithToolbarPanel("Reference", showToolbar = false, showTitle = true)

  private val multiViewPanels = listOf(newImagePanel, diffImagePanel, refImagePanel)

  // Panels for the individual tabbed views in single preview mode.
  private val newImagePanelSingle = ImageWithToolbarPanel("New", showToolbar = true, showTitle = false)
  private val diffImagePanelSingle = ImageWithToolbarPanel("Diff", showToolbar = true, showTitle = false)
  private val refImagePanelSingle = ImageWithToolbarPanel("Reference", showToolbar = true, showTitle = false)

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
   * Configures and displays the detailed view for a single screenshot preview.
   * This view includes the image(s), metadata attributes, and a toolbar.
   */
  private fun displaySinglePreviewDetails(
    previewData: PreviewDetails,
    viewType: UpdateReferenceImagesDialog.ScreenshotViewType,
    previewToolbar: ComposePanel
  ) {
    singlePreviewPanel.removeAll()

    val topContainerPanel = JPanel(BorderLayout()).apply {
      minimumSize = Dimension(0, 200)
    }
    val titleLabel = JBLabel(previewData.previewName).apply {
      font = font.deriveFont(Font.BOLD, font.size + 4f)
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    topContainerPanel.add(titleLabel, BorderLayout.NORTH)

    // Add the appropriate image view (either the 3-way split or the tabbed single view).
    val imageDisplayPanel = if (viewType == UpdateReferenceImagesDialog.ScreenshotViewType.ALL) {
      setupAllImagesView(previewData)
    } else {
      setupSingleImageView(previewData, viewType)
    }
    topContainerPanel.add(imageDisplayPanel, BorderLayout.CENTER)
    topContainerPanel.add(previewToolbar, BorderLayout.SOUTH)

    // Setup the bottom panel with screenshot attributes.
    val detailsPanel = screenshotAttributesView.getComponent().apply {
      minimumSize = Dimension(0, 120)
    }
    updateScreenshotAttributesView(previewData)

    // Combine the top and bottom panels in a splitter.
    val splitter = JBSplitter(true, 0.65f).apply {
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
    viewType: UpdateReferenceImagesDialog.ScreenshotViewType
  ): JComponent {
    val imageContainer = JPanel(CardLayout())
    imageContainer.add(newImagePanelSingle, UpdateReferenceImagesDialog.ScreenshotViewType.NEW.displayText)
    imageContainer.add(diffImagePanelSingle, UpdateReferenceImagesDialog.ScreenshotViewType.DIFF.displayText)
    imageContainer.add(refImagePanelSingle, UpdateReferenceImagesDialog.ScreenshotViewType.REFERENCE.displayText)

    val cardLayout = imageContainer.layout as CardLayout
    val diffPlaceholder = if (previewData.testResult == AndroidTestCaseResult.PASSED) "No Difference" else "No Diff Image"

    when (viewType) {
      UpdateReferenceImagesDialog.ScreenshotViewType.NEW ->
        loadImageAsync(previewData.srcImagePath, newImagePanelSingle, "No New Image")
      UpdateReferenceImagesDialog.ScreenshotViewType.DIFF ->
        loadImageAsync(previewData.diffImagePath, diffImagePanelSingle, diffPlaceholder)
      UpdateReferenceImagesDialog.ScreenshotViewType.REFERENCE ->
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

  /**
   * A self-contained panel for displaying an image with an optional title and toolbar
   * for zoom and other view controls.
   */
  private class ImageWithToolbarPanel(
    private val title: String,
    private val showToolbar: Boolean,
    private val showTitle: Boolean
  ) : JPanel(BorderLayout(0, 4)) {
    private val imageLabel = object : JBLabel() {
      private var gridVisible = false
      private var chessboardVisible = false

      fun setGridVisible(visible: Boolean) {
        if (gridVisible != visible) {
          gridVisible = visible
          repaint()
        }
      }
      fun isGridVisible(): Boolean = gridVisible
      fun setChessboardVisible(visible: Boolean) {
        if (chessboardVisible != visible) {
          chessboardVisible = visible
          repaint()
        }
      }
      fun isChessboardVisible(): Boolean = chessboardVisible

      override fun paintComponent(g: Graphics) {
        if (chessboardVisible) {
          val g2d = g.create() as Graphics2D
          try {
            val color1 = UIUtil.getPanelBackground()
            val color2 = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            val squareSize = 8
            var x = 0
            while (x < width) {
              var y = 0
              while (y < height) {
                g2d.color = if ((x / squareSize + y / squareSize) % 2 == 0) color1 else color2
                g2d.fillRect(x, y, squareSize, squareSize)
                y += squareSize
              }
              x += squareSize
            }
          }
          finally {
            g2d.dispose()
          }
        }

        super.paintComponent(g)
        if (icon == null) return
        val g2d = g.create() as Graphics2D
        try {
          val iconX = (width - icon.iconWidth) / 2
          val iconY = (height - icon.iconHeight) / 2
          if (title == "Diff") {
            g2d.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            g2d.drawRect(iconX, iconY, icon.iconWidth - 1, icon.iconHeight - 1)
          }
          if (gridVisible) {
            g2d.translate(iconX, iconY)
            g2d.color = Color(128, 128, 128, 128)
            val gridSize = (20 * currentScale).toInt().coerceAtLeast(1)
            for (x in 0..icon.iconWidth step gridSize) g2d.drawLine(x, 0, x, icon.iconHeight)
            for (y in 0..icon.iconHeight step gridSize) g2d.drawLine(0, y, icon.iconWidth, y)
          }
        } finally {
          g2d.dispose()
        }
      }
    }.apply {
      horizontalAlignment = SwingConstants.CENTER
    }

    private val placeholderLabel = JBLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.CENTER
    }

    internal val scrollPane = JScrollPane()
    private val imageContainer = JPanel(BorderLayout())
    private var originalImage: BufferedImage? = null
    private var currentScale = 1.0
    private var isAutoFitting = false

    val zoomInAction = object : AnAction("Zoom In", null, AllIcons.General.ZoomIn) {
      override fun actionPerformed(e: AnActionEvent) = zoomIn()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = canZoomIn() }
    }
    val zoomOutAction = object : AnAction("Zoom Out", null, AllIcons.General.ZoomOut) {
      override fun actionPerformed(e: AnActionEvent) = zoomOut()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = canZoomOut() }
    }
    val oneToOneAction = object : AnAction("1:1", "Actual Size", AllIcons.General.ActualZoom) {
      override fun actionPerformed(e: AnActionEvent) = setActualSize()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }
    val fitToScreenAction = object : AnAction("Fit to Screen", "Fit image to screen", AllIcons.General.FitContent) {
      override fun actionPerformed(e: AnActionEvent) = fitToScreen()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }
    val toggleGridViewAction = object : ToggleAction("Grid", "Toggle Grid Overlay", AllIcons.Graph.Grid) {
      override fun isSelected(e: AnActionEvent): Boolean = isGridVisible()
      override fun setSelected(e: AnActionEvent, state: Boolean) = setGridVisible(state)
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }
    val toggleChessboardAction =
      object : ToggleAction("Chessboard", "Toggle Chessboard Background", IconLoader.getIcon("/org/intellij/images/icons/expui/chessboard.svg", PreviewDetailsPanel::class.java)) {
        override fun isSelected(e: AnActionEvent): Boolean = isChessboardVisible()
        override fun setSelected(e: AnActionEvent, state: Boolean) = setChessboardVisible(state)
        override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
      }

    private var dynamicMinScale = 0.1
    private var dynamicMaxScale = 8.0

    init {
      border = JBUI.Borders.empty(if (showTitle) 10 else 0, 10, 10, 10)
      val actionGroup = DefaultActionGroup().apply {
        add(toggleChessboardAction)
        add(toggleGridViewAction)
        addSeparator()
        add(zoomOutAction)
        add(zoomInAction)
        add(oneToOneAction)
        add(fitToScreenAction)
      }
      val toolbar = ActionManager.getInstance().createActionToolbar("ScreenshotImageToolbar", actionGroup, true).apply {
        targetComponent = this@ImageWithToolbarPanel
      }

      if (showTitle || showToolbar) {
        val headerPanel = JPanel(BorderLayout())
        if (showTitle) {
          val titleLabel = JBLabel(title, UIUtil.ComponentStyle.LARGE)
          titleLabel.horizontalAlignment = SwingConstants.LEFT
          headerPanel.add(titleLabel, BorderLayout.NORTH)
        }
        if (showToolbar) {
          val toolbarWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
          toolbarWrapper.add(toolbar.component)
          headerPanel.add(toolbarWrapper, BorderLayout.SOUTH)
        }
        add(headerPanel, BorderLayout.NORTH)
      }
      imageContainer.background = UIUtil.getPanelBackground()
      imageContainer.add(imageLabel, BorderLayout.CENTER)
      scrollPane.setViewportView(imageContainer)
      add(scrollPane, BorderLayout.CENTER)
      scrollPane.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          if (isAutoFitting) fitToScreen()
        }
      })
    }

    fun zoomIn() {
      isAutoFitting = false
      currentScale = (currentScale * 1.2).coerceAtMost(dynamicMaxScale)
      updateImage()
    }

    fun zoomOut() {
      isAutoFitting = false
      currentScale = (currentScale / 1.2).coerceAtLeast(dynamicMinScale)
      updateImage()
    }

    fun setActualSize() {
      isAutoFitting = false
      currentScale = 1.0
      updateImage()
    }

    fun fitToScreen() {
      isAutoFitting = true
      val image = originalImage ?: return
      val viewSize = scrollPane.viewport.extentSize
      if (viewSize.width <= 0 || viewSize.height <= 0 || image.width <= 0 || image.height <= 0) return
      val widthScale = viewSize.width.toDouble() / image.width
      val heightScale = viewSize.height.toDouble() / image.height
      val fitScale = min(widthScale, heightScale)
      dynamicMaxScale = max(fitScale, 1.0) * 2.5
      dynamicMinScale = min(fitScale, 1.0) / 2.5
      currentScale = fitScale
      updateImage()
    }

    fun setGridVisible(visible: Boolean) = imageLabel.setGridVisible(visible)
    fun isGridVisible(): Boolean = imageLabel.isGridVisible()
    fun setChessboardVisible(visible: Boolean) = imageLabel.setChessboardVisible(visible)
    fun isChessboardVisible(): Boolean = imageLabel.isChessboardVisible()
    fun canZoomIn(): Boolean = originalImage != null && currentScale < dynamicMaxScale
    fun canZoomOut(): Boolean = originalImage != null && currentScale > dynamicMinScale
    fun hasImage(): Boolean = originalImage != null

    fun setPlaceholder(text: String) {
      placeholderLabel.text = text
    }

    fun setImage(image: BufferedImage?) {
      originalImage = image
      if (image == null) {
        imageLabel.icon = null
        scrollPane.setViewportView(placeholderLabel)
      } else {
        scrollPane.setViewportView(imageContainer)
        isAutoFitting = true
        fitToScreen()
      }
      revalidate()
      repaint()
    }

    private fun updateImage() {
      val image = originalImage ?: return
      val newWidth = (image.width * currentScale).toInt()
      val newHeight = (image.height * currentScale).toInt()
      if (newWidth > 0 && newHeight > 0) {
        val imageType = if (image.type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB else image.type
        val scaledImage = BufferedImage(newWidth, newHeight, imageType)
        val g2d = scaledImage.createGraphics()
        try {
          g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
          g2d.drawImage(image, 0, 0, newWidth, newHeight, null)
        } finally {
          g2d.dispose()
        }
        imageLabel.icon = ImageIcon(scaledImage)
      }
      scrollPane.revalidate()
      scrollPane.repaint()
    }
  }
}