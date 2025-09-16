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
import java.awt.GridBagLayout
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
import kotlin.math.min

/**
 * This is a placeholder for showing Screenshot Test Results.
 */
class ScreenshotResultView {

  val myView: JPanel = JPanel(BorderLayout())

  // Panels for the "All" tab
  @VisibleForTesting
  val newImagePanel = ImageWithToolbarPanel("New", this, true)
  @VisibleForTesting
  val diffImagePanel = ImageWithToolbarPanel("Diff", this, true)
  @VisibleForTesting
  val refImagePanel = ImageWithToolbarPanel("Reference", this, true)

  // Panels for the single-view tabs
  @VisibleForTesting
  val newImagePanelSingle = ImageWithToolbarPanel("New", this, false)
  @VisibleForTesting
  val diffImagePanelSingle = ImageWithToolbarPanel("Diff", this, false)
  @VisibleForTesting
  val refImagePanelSingle = ImageWithToolbarPanel("Reference", this, false)

  private val contentPanel = JPanel(CardLayout())
  private val tabBar = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
  private val tabGroup = ButtonGroup()

  var newImagePath: String = ""
  var refImagePath: String = ""
  var diffImagePath: String = ""
  var testFailed: Boolean = false

  private var areScrollbarsLinked = false

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

    addTab("All", mainSplit)
    addTab("New", newImagePanelSingle)
    addTab("Diff", diffImagePanelSingle)
    addTab("Reference", refImagePanelSingle)

    //Selecting the "All" tab by default
    (tabBar.components.firstOrNull() as? JToggleButton)?.isSelected = true

    myView.add(contentPanel, BorderLayout.CENTER)
    myView.add(tabBar, BorderLayout.SOUTH)
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

  fun setScrollbarLinking(link: Boolean) {
    if (link == areScrollbarsLinked) return
    areScrollbarsLinked = link

    if (link) {
      diffImagePanel.linkScrollbars(newImagePanel)
      refImagePanel.linkScrollbars(newImagePanel)
    } else {
      diffImagePanel.unlinkScrollbars()
      refImagePanel.unlinkScrollbars()
    }
    // Force all toolbars to update their action states
    newImagePanel.updateToolbar()
    diffImagePanel.updateToolbar()
    refImagePanel.updateToolbar()
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
    title: String,
    private val parentView: ScreenshotResultView,
    private val showSyncPanAction: Boolean
  ) : JPanel(BorderLayout(0, 4)) {
    private val imageLabel = object : JBLabel() {
      private var gridVisible = false
      fun setGridVisible(visible: Boolean) {
        if (gridVisible != visible) {
          gridVisible = visible
          repaint()
        }
      }

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g) // This will draw the icon
        if (gridVisible && icon != null) {
          val g2d = g.create() as Graphics2D
          try {
            g2d.color = Color(128, 128, 128, 128)
            val gridSize = (20 * currentScale).toInt().coerceAtLeast(1)

            val iconX = (width - icon.iconWidth) / 2
            val iconY = (height - icon.iconHeight) / 2
            g2d.translate(iconX, iconY)

            for (x in 0..icon.iconWidth step gridSize) {
              g2d.drawLine(x, 0, x, icon.iconHeight)
            }
            for (y in 0..icon.iconHeight step gridSize) {
              g2d.drawLine(0, y, icon.iconWidth, y)
            }
          } finally {
            g2d.dispose()
          }
        }
      }
    }.apply {
      // Add a subtle, theme-aware border to clearly delineate the image boundaries
      // since diff image is transparent in the areas where new and reference images match
      border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    }
    private val placeholderLabel = JBLabel().apply {
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.CENTER
    }
    private val scrollPane = JScrollPane()
    private val imageWrapperPanel = JPanel(GridBagLayout())

    private val toolbar: ActionToolbar
    private var originalImage: BufferedImage? = null
    private var currentScale = 1.0
    private var isAutoFitting = false

    private val originalHorizontalModel: BoundedRangeModel = scrollPane.horizontalScrollBar.model
    private val originalVerticalModel: BoundedRangeModel = scrollPane.verticalScrollBar.model

    companion object {
      private const val MIN_SCALE = 0.1
      private const val MAX_SCALE = 1.8
      private const val ZOOM_FACTOR = 1.2
      private const val SINGLE_TAB_MAX_IMAGE_WIDTH = 500
    }

    // Toolbar actions
    private inner class ZoomInAction : AnAction("Zoom In", null, AllIcons.General.ZoomIn) {
      override fun actionPerformed(e: AnActionEvent) {
        isAutoFitting = false
        currentScale = (currentScale * ZOOM_FACTOR).coerceAtMost(MAX_SCALE)
        updateImage()
      }
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = originalImage != null && currentScale < MAX_SCALE
      }
    }

    private inner class ZoomOutAction : AnAction("Zoom Out", null, AllIcons.General.ZoomOut) {
      override fun actionPerformed(e: AnActionEvent) {
        isAutoFitting = false
        currentScale = (currentScale / ZOOM_FACTOR).coerceAtLeast(MIN_SCALE)
        updateImage()
      }
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = originalImage != null && currentScale > MIN_SCALE
      }
    }

    private inner class OneToOneAction : AnAction("1:1", "Actual Size", AllIcons.General.ActualZoom) {
      override fun actionPerformed(e: AnActionEvent) {
        isAutoFitting = false
        currentScale = 1.0
        updateImage()
      }
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = (originalImage != null) }
    }

    private inner class FitToWidthAction : AnAction("Fit to Width", "Fit image to panel width", AllIcons.General.FitContent) {
      override fun actionPerformed(e: AnActionEvent) {
        isAutoFitting = true
        fitToWidth()
      }
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = (originalImage != null) }
    }

    private inner class ToggleGridViewAction : ToggleAction("Grid", "Toggle Grid Overlay", AllIcons.Graph.Grid) {
      private var selected = false
      override fun isSelected(e: AnActionEvent): Boolean = selected
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        selected = state
        imageLabel.setGridVisible(state)
      }
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = (originalImage != null) }
    }

    private inner class SynchronizedPanAction : ToggleAction("Sync Pan", "Synchronize Scrolling", AllIcons.Actions.SyncPanels) {
      override fun isSelected(e: AnActionEvent): Boolean = parentView.areScrollbarsLinked
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        parentView.setScrollbarLinking(state)
      }
      override fun update(e: AnActionEvent) { e.presentation.isEnabled = (originalImage != null) }
    }

    init {
      border = JBUI.Borders.empty(10)

      val headerPanel = JPanel(BorderLayout(0, 4))
      val titleLabel = JBLabel(title, UIUtil.ComponentStyle.LARGE)
      titleLabel.horizontalAlignment = SwingConstants.LEFT
      headerPanel.add(titleLabel, BorderLayout.NORTH)

      val actionGroup = DefaultActionGroup().apply {
        add(ZoomInAction())
        add(ZoomOutAction())
        add(OneToOneAction())
        add(FitToWidthAction())
        addSeparator()
        add(ToggleGridViewAction())
        if (showSyncPanAction) {
          add(SynchronizedPanAction())
        }
      }
      toolbar = ActionManager.getInstance().createActionToolbar("ScreenshotImageToolbar", actionGroup, true).apply {
        targetComponent = this@ImageWithToolbarPanel
      }

      val toolbarWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
      toolbarWrapper.add(toolbar.component)
      headerPanel.add(toolbarWrapper, BorderLayout.CENTER)

      add(headerPanel, BorderLayout.NORTH)

      imageWrapperPanel.background = scrollPane.viewport.background
      imageWrapperPanel.add(imageLabel)
      add(scrollPane, BorderLayout.CENTER)

      scrollPane.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          if (isAutoFitting) {
            fitToWidth()
          }
        }
      })
    }

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
      } else {
        scrollPane.setViewportView(imageWrapperPanel)
        // When a new image is set, always default to auto-fitting.
        isAutoFitting = true
        fitToWidth()
      }
      revalidate()
      repaint()
    }

    fun linkScrollbars(other: ImageWithToolbarPanel) {
      scrollPane.verticalScrollBar.model = other.scrollPane.verticalScrollBar.model
      scrollPane.horizontalScrollBar.model = other.scrollPane.horizontalScrollBar.model
    }

    fun unlinkScrollbars() {
      scrollPane.verticalScrollBar.model = originalVerticalModel
      scrollPane.horizontalScrollBar.model = originalHorizontalModel
    }

    fun updateToolbar() {
      toolbar.updateActionsImmediately()
    }

    @UiThread
    private fun fitToWidth() {
      val image = originalImage ?: return
      val viewSize = scrollPane.viewport.extentSize
      if (viewSize.width <= 0 || viewSize.height <= 0 || image.width <= 0) return

      val scale = if (showSyncPanAction) {
        viewSize.width.toDouble() / image.width
      } else {
        val targetWidth = min(viewSize.width, SINGLE_TAB_MAX_IMAGE_WIDTH)
        min(1.0, targetWidth.toDouble() / image.width)
      }

      currentScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
      updateImage()
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
      toolbar.updateActionsImmediately()
    }
  }
}