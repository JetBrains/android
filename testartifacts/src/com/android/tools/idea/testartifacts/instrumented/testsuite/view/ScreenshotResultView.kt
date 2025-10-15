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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.annotations.concurrency.UiThread
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ItemEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BoundedRangeModel
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.min

/**
 * This is a placeholder for showing Screenshot Test Results.
 */
class ScreenshotResultView {

  val myView: JPanel = JPanel(BorderLayout())

  // Panels for the "All" tab (common toolbar, individual titles)
  @VisibleForTesting
  val newImagePanel = ImageWithToolbarPanel("New", showToolbar = false, showTitle = true)
  @VisibleForTesting
  val diffImagePanel = ImageWithToolbarPanel("Diff", showToolbar = false, showTitle = true)
  @VisibleForTesting
  val refImagePanel = ImageWithToolbarPanel("Reference", showToolbar = false, showTitle = true)

  private val multiViewPanels = listOf(newImagePanel, diffImagePanel, refImagePanel)

  // Panels for the single-view tabs (with individual toolbars and titles)
  @VisibleForTesting
  val newImagePanelSingle = ImageWithToolbarPanel("New", showToolbar = true, showTitle = true)
  @VisibleForTesting
  val diffImagePanelSingle = ImageWithToolbarPanel("Diff", showToolbar = true, showTitle = true)
  @VisibleForTesting
  val refImagePanelSingle = ImageWithToolbarPanel("Reference", showToolbar = true, showTitle = true)

  private val contentPanel = JPanel(CardLayout())
  private val tabBar = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
  private val tabGroup = ButtonGroup()

  var newImagePath: String = ""
  var refImagePath: String = ""
  var diffImagePath: String = ""
  var testFailed: Boolean = false

  // Expose common actions for testing
  @VisibleForTesting
  val commonZoomInAction = object : AnAction("Zoom In", null, AllIcons.General.ZoomIn) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.zoomIn() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.canZoomIn() }
    }
  }
  @VisibleForTesting
  val commonZoomOutAction = object : AnAction("Zoom Out", null, AllIcons.General.ZoomOut) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.zoomOut() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.canZoomOut() }
    }
  }
  @VisibleForTesting
  val commonOneToOneAction = object : AnAction("1:1", "Actual Size", AllIcons.General.ActualZoom) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.setActualSize() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
    }
  }
  @VisibleForTesting
  val commonFitToScreenAction = object : AnAction("Fit to Screen", "Fit image to screen", AllIcons.General.FitContent) {
    override fun actionPerformed(e: AnActionEvent) = multiViewPanels.forEach { it.fitToScreen() }
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
    }
  }
  @VisibleForTesting
  val commonToggleGridViewAction = object : ToggleAction("Grid", "Toggle Grid Overlay", AllIcons.Graph.Grid) {
    override fun isSelected(e: AnActionEvent): Boolean {
      // The state should be the same for all, so checking the first is enough.
      return multiViewPanels.firstOrNull()?.isGridVisible() ?: false
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      multiViewPanels.forEach { it.setGridVisible(state) }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = multiViewPanels.any { it.hasImage() }
    }
  }

  init {
    // Use nested OnePixelSplitters to create a three-panel view
    val rightSplit = OnePixelSplitter(false, 0.5f).apply {
      firstComponent = diffImagePanel
      secondComponent = refImagePanel
    }

    val mainSplit = OnePixelSplitter(false, 0.33f).apply {
      firstComponent = newImagePanel
      secondComponent = rightSplit
    }

    // Create a new panel for the "All" tab that includes the common toolbar
    val allTabPanel = JPanel(BorderLayout())
    val commonToolbar = createCommonToolbar()
    allTabPanel.add(commonToolbar.component, BorderLayout.NORTH)
    allTabPanel.add(mainSplit, BorderLayout.CENTER)

    // Link the scrollbars for the three-panel view by default.
    // This is done by listening to changes in each scrollbar's model and propagating
    // the new value to the others. This is more robust than sharing the model
    // instance directly, as it avoids layout conflicts when image sizes differ.
    val horizontalModels = multiViewPanels.map { it.scrollPane.horizontalScrollBar.model }
    val verticalModels = multiViewPanels.map { it.scrollPane.verticalScrollBar.model }

    val horizontalSyncListener = object : ChangeListener {
      var isSyncing = false
      override fun stateChanged(e: ChangeEvent) {
        if (isSyncing) return

        try {
          isSyncing = true
          val sourceModel = e.source as BoundedRangeModel
          val newValue = sourceModel.value
          horizontalModels.forEach { model ->
            if (model !== sourceModel) {
              model.value = newValue
            }
          }
        } finally {
          isSyncing = false
        }
      }
    }

    val verticalSyncListener = object : ChangeListener {
      var isSyncing = false
      override fun stateChanged(e: ChangeEvent) {
        if (isSyncing) return

        try {
          isSyncing = true
          val sourceModel = e.source as BoundedRangeModel
          val newValue = sourceModel.value
          verticalModels.forEach { model ->
            if (model !== sourceModel) {
              model.value = newValue
            }
          }
        } finally {
          isSyncing = false
        }
      }
    }

    horizontalModels.forEach { it.addChangeListener(horizontalSyncListener) }
    verticalModels.forEach { it.addChangeListener(verticalSyncListener) }

    fun addTab(title: String, component: JComponent) {
      val cardLayout = contentPanel.layout as CardLayout
      contentPanel.add(component, title)

      val selectedBorder = JBUI.Borders.customLine(JBUI.CurrentTheme.Focus.focusColor(), 0, 0, 3, 0)
      val unselectedBorder = JBUI.Borders.emptyBottom(3)

      val tabButton = JToggleButton(title).apply {
        isFocusable = false

        isContentAreaFilled = false
        border = unselectedBorder
        font = font.deriveFont(Font.PLAIN)

        // ItemListener to react to selection changes to let users know which tab is getting viewed
        addItemListener { e ->
          if (e.stateChange == ItemEvent.SELECTED) {
            // When this tab is selected, show its card, make the text bold, and show the underline.
            cardLayout.show(contentPanel, title)
            font = font.deriveFont(Font.BOLD)
            border = selectedBorder
          } else if (e.stateChange == ItemEvent.DESELECTED) {
            // When deselected, revert the font and border.
            font = font.deriveFont(Font.PLAIN)
            border = unselectedBorder
          }
        }
      }

      tabGroup.add(tabButton)
      tabBar.add(tabButton)
    }

    addTab("All", allTabPanel)
    addTab("New", newImagePanelSingle)
    addTab("Diff", diffImagePanelSingle)
    addTab("Reference", refImagePanelSingle)

    //Selecting the "All" tab by default
    (tabBar.components.firstOrNull() as? JToggleButton)?.isSelected = true

    myView.add(contentPanel, BorderLayout.CENTER)
    myView.add(tabBar, BorderLayout.SOUTH)
  }

  private fun createCommonToolbar(): ActionToolbar {
    val actionGroup = DefaultActionGroup().apply {
      add(commonZoomInAction)
      add(commonZoomOutAction)
      add(commonOneToOneAction)
      add(commonFitToScreenAction)
      addSeparator()
      add(commonToggleGridViewAction)
    }
    return ActionManager.getInstance().createActionToolbar("ScreenshotCommonToolbar", actionGroup, true).apply {
      targetComponent = myView
    }
  }

  @UiThread
  fun getComponent(): JComponent {
    return myView
  }

  @UiThread
  fun updateView() {
    val diffPlaceholder = if (testFailed) "No Diff Image" else "No Difference"

    // Load images for the "All" tab
    loadImageAsync(newImagePath, newImagePanel, "No Preview Image")
    loadImageAsync(diffImagePath, diffImagePanel, diffPlaceholder)
    loadImageAsync(refImagePath, refImagePanel, "No Reference Image")

    // Load images for the single-view tabs
    loadImageAsync(newImagePath, newImagePanelSingle, "No Preview Image")
    loadImageAsync(diffImagePath, diffImagePanelSingle, diffPlaceholder)
    loadImageAsync(refImagePath, refImagePanelSingle, "No Reference Image")

    myView.revalidate()
    myView.repaint()
  }

  /** Loads an image from a file path on a background thread and sets it on the target panel. */
  private fun loadImageAsync(filePath: String, targetPanel: ImageWithToolbarPanel, placeholder: String) {
    targetPanel.setPlaceholder(placeholder)
    AppExecutorUtil.getAppExecutorService().submit {
      val image = try {
        val file = File(filePath)
        if (file.exists()) ImageIO.read(file) else null
      } catch (e: Exception) {
        null
      }

      UIUtil.invokeLaterIfNeeded {
        targetPanel.setImage(image)
      }
    }
  }

  /**
   * A self-contained panel that displays an image with a title and a toolbar for zoom controls.
   */
  @VisibleForTesting
  class ImageWithToolbarPanel(
    private val title: String,
    showToolbar: Boolean,
    showTitle: Boolean
  ) : JPanel(BorderLayout(0, 4)) {
    private val imageLabel = object : JBLabel() {
      private var gridVisible = false
      fun setGridVisible(visible: Boolean) {
        if (gridVisible != visible) {
          gridVisible = visible
          repaint()
        }
      }
      fun isGridVisible(): Boolean = gridVisible

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g) // This will draw the icon (the actual image)

        if (icon == null) return

        val g2d = g.create() as Graphics2D
        try {
          val iconX = (width - icon.iconWidth) / 2
          val iconY = (height - icon.iconHeight) / 2

          // Only draw the border for the "Diff" image panel.
          if (title == "Diff") {
            // Draw a subtle, theme-aware border around the actual image to clearly delineate its boundaries.
            g2d.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            g2d.drawRect(iconX, iconY, icon.iconWidth - 1, icon.iconHeight - 1)
          }

          if (gridVisible) {
            // The grid is drawn relative to the image's top-left corner.
            g2d.translate(iconX, iconY)
            g2d.color = Color(128, 128, 128, 128)
            val gridSize = (20 * currentScale).toInt().coerceAtLeast(1)

            for (x in 0..icon.iconWidth step gridSize) {
              g2d.drawLine(x, 0, x, icon.iconHeight)
            }
            for (y in 0..icon.iconHeight step gridSize) {
              g2d.drawLine(0, y, icon.iconWidth, y)
            }
          }
        }
        finally {
          g2d.dispose()
        }
      }
    }.apply {
      // The border is now drawn in paintComponent to match the image's exact bounds.
      horizontalAlignment = SwingConstants.CENTER
    }
    private val placeholderLabel = JBLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.CENTER
    }
    internal val scrollPane = JScrollPane()
    // A simple, non-opaque panel to wrap the image label. This resolves a Swing layout conflict
    // that was preventing the scrollbars from appearing correctly.
    private val imageContainer = JPanel(BorderLayout())

    @VisibleForTesting
    val toolbar: ActionToolbar
    private var originalImage: BufferedImage? = null
    @VisibleForTesting
    var currentScale = 1.0
    @VisibleForTesting
    var isAutoFitting = false

    // Expose actions for testing
    @VisibleForTesting
    val zoomInAction = object : AnAction("Zoom In", null, AllIcons.General.ZoomIn) {
      override fun actionPerformed(e: AnActionEvent) = zoomIn()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = canZoomIn() }
    }
    @VisibleForTesting
    val zoomOutAction = object : AnAction("Zoom Out", null, AllIcons.General.ZoomOut) {
      override fun actionPerformed(e: AnActionEvent) = zoomOut()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = canZoomOut() }
    }
    @VisibleForTesting
    val oneToOneAction = object : AnAction("1:1", "Actual Size", AllIcons.General.ActualZoom) {
      override fun actionPerformed(e: AnActionEvent) = setActualSize()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }
    @VisibleForTesting
    val fitToScreenAction = object : AnAction("Fit to Screen", "Fit image to screen", AllIcons.General.FitContent) {
      override fun actionPerformed(e: AnActionEvent) = fitToScreen()
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }
    @VisibleForTesting
    val toggleGridViewAction = object : ToggleAction("Grid", "Toggle Grid Overlay", AllIcons.Graph.Grid) {
      override fun isSelected(e: AnActionEvent): Boolean = isGridVisible()
      override fun setSelected(e: AnActionEvent, state: Boolean) = setGridVisible(state)
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }

    companion object {
      private const val MIN_SCALE = 0.1
      private const val MAX_SCALE = 1.8
      private const val ZOOM_FACTOR = 1.2
    }

    init {
      border = JBUI.Borders.empty(if (showTitle) 10 else 0, 10, 10, 10)

      val actionGroup = DefaultActionGroup().apply {
        add(zoomInAction)
        add(zoomOutAction)
        add(oneToOneAction)
        add(fitToScreenAction)
        addSeparator()
        add(toggleGridViewAction)
      }
      toolbar = ActionManager.getInstance().createActionToolbar("ScreenshotImageToolbar", actionGroup, true).apply {
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
          // To align the toolbar with the title, we add it to the SOUTH.
          // Since the title is in NORTH, this works well.
          headerPanel.add(toolbarWrapper, BorderLayout.SOUTH)
        }
        add(headerPanel, BorderLayout.NORTH)
      }

      imageContainer.isOpaque = false
      imageContainer.add(imageLabel, BorderLayout.CENTER)
      add(scrollPane, BorderLayout.CENTER)

      scrollPane.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          if (isAutoFitting) {
            fitToScreen()
          }
        }
      })
    }

    // Public API for external control
    fun zoomIn() {
      isAutoFitting = false
      currentScale = (currentScale * ZOOM_FACTOR).coerceAtMost(MAX_SCALE)
      updateImage()
    }

    fun zoomOut() {
      isAutoFitting = false
      currentScale = (currentScale / ZOOM_FACTOR).coerceAtLeast(MIN_SCALE)
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
      val scale = min(widthScale, heightScale)

      currentScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
      updateImage()
    }

    fun setGridVisible(visible: Boolean) = imageLabel.setGridVisible(visible)
    fun isGridVisible(): Boolean = imageLabel.isGridVisible()
    fun canZoomIn(): Boolean = originalImage != null && currentScale < MAX_SCALE
    fun canZoomOut(): Boolean = originalImage != null && currentScale > MIN_SCALE
    fun hasImage(): Boolean = originalImage != null

    @UiThread
    fun setPlaceholder(text: String) {
      placeholderLabel.text = text
    }

    @UiThread
    fun setImage(image: BufferedImage?) {
      originalImage = image
      if (image == null) {
        imageLabel.icon = null
        scrollPane.setViewportView(placeholderLabel)
        // Explicitly update the toolbar actions to disable them for null images.
        toolbar.updateActionsAsync()
      } else {
        scrollPane.setViewportView(imageContainer)
        // When a new image is set, always default to auto-fitting.
        isAutoFitting = true
        fitToScreen()
      }
      revalidate()
      repaint()
    }

    @UiThread
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
      // After updating the image and scale, we should update the toolbar
      // to reflect the new enabled/disabled state of the zoom buttons.
      toolbar.updateActionsAsync()

      // Revalidate the scroll pane directly to ensure it re-evaluates its viewport and scrollbars.
      scrollPane.revalidate()
      scrollPane.repaint()
    }
  }
}