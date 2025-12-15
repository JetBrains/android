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
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import kotlin.math.max
import kotlin.math.min

/**
 * A self-contained panel that displays an image with a title and a toolbar for zoom controls.
 */
class ImageWithToolbarPanel(
  val title: ScreenshotViewType,
  showToolbar: Boolean,
  showTitle: Boolean
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

      super.paintComponent(g) // This will draw the icon (the actual image)

      if (icon == null) return

      val g2d = g.create() as Graphics2D
      try {
        val iconX = (width - icon.iconWidth) / 2
        val iconY = (height - icon.iconHeight) / 2

        // Only draw the border for the "Diff" image panel.
        if (title == ScreenshotViewType.DIFF) {
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
  val scrollPane = JScrollPane()
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
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() && currentScale != 1.0 }
  }
  @VisibleForTesting
  val fitToScreenAction = object : AnAction("Fit to Screen", "Fit image to screen", AllIcons.General.FitContent) {
    override fun actionPerformed(e: AnActionEvent) = fitToScreen()
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() && !isAutoFitting }
  }
  @VisibleForTesting
  val toggleGridViewAction = object : ToggleAction("Grid", "Toggle Grid Overlay", AllIcons.Graph.Grid) {
    override fun isSelected(e: AnActionEvent): Boolean = isGridVisible()
    override fun setSelected(e: AnActionEvent, state: Boolean) = setGridVisible(state)
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
  }

  @VisibleForTesting
  val toggleChessboardAction =
    object : ToggleAction("Chessboard", "Toggle Chessboard Background", IconLoader.getIcon("/org/intellij/images/icons/expui/chessboard.svg", ImageWithToolbarPanel::class.java)) {
      override fun isSelected(e: AnActionEvent): Boolean = isChessboardVisible()
      override fun setSelected(e: AnActionEvent, state: Boolean) = setChessboardVisible(state)
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = hasImage() }
    }

  private var dynamicMinScale = 0.1
  private var dynamicMaxScale = 8.0

  companion object {
    private const val ZOOM_FACTOR = 1.2
    private const val MAX_ZOOM_FACTOR_BEYOND_ANCHOR = 2.5
    private const val MIN_ZOOM_FACTOR_BEYOND_ANCHOR = 2.5
  }

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
    toolbar = ActionManager.getInstance().createActionToolbar("ScreenshotImageToolbar", actionGroup, true).apply {
      targetComponent = this@ImageWithToolbarPanel
    }

    if (showTitle || showToolbar) {
      val headerPanel = JPanel(BorderLayout())
      if (showTitle) {
        val titleLabel = JBLabel(title.displayText, UIUtil.ComponentStyle.LARGE)
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
    currentScale = (currentScale * ZOOM_FACTOR).coerceAtMost(dynamicMaxScale)
    updateImage()
  }

  fun zoomOut() {
    isAutoFitting = false
    currentScale = (currentScale / ZOOM_FACTOR).coerceAtLeast(dynamicMinScale)
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

    // Set the dynamic zoom range based on the fit-to-screen and actual size scales.
    val actualScale = 1.0
    dynamicMaxScale = max(fitScale, actualScale) * MAX_ZOOM_FACTOR_BEYOND_ANCHOR
    dynamicMinScale = min(fitScale, actualScale) / MIN_ZOOM_FACTOR_BEYOND_ANCHOR

    // Set the current scale to the calculated fit-to-screen scale without coercing it.
    // This ensures the initial view is perfectly fit. Coercion only applies to subsequent zoom actions.
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
      if (toolbar.component.isDisplayable) {
        toolbar.updateActionsAsync()
      }
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
    if (toolbar.component.isDisplayable) {
      toolbar.updateActionsAsync()
    }

    // Revalidate the scroll pane directly to ensure it re-evaluates its viewport and scrollbars.
    scrollPane.revalidate()
    scrollPane.repaint()
  }
}
