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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
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
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BoundedRangeModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text


/**
 * This is a placeholder for showing Screenshot Test Results.
 */
class ScreenshotResultView {

  val myView: JPanel = JPanel(BorderLayout())

  // Panels for the "All" tab (common toolbar, individual titles)
  @VisibleForTesting
  val newImagePanel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = false, showTitle = true)
  @VisibleForTesting
  val diffImagePanel = ImageWithToolbarPanel(ScreenshotViewType.DIFF, showToolbar = false, showTitle = true)
  @VisibleForTesting
  val refImagePanel = ImageWithToolbarPanel(ScreenshotViewType.REFERENCE, showToolbar = false, showTitle = true)

  private val multiViewPanels = listOf(newImagePanel, diffImagePanel, refImagePanel)

  // Panels for the single-view tabs (with individual toolbars and titles)
  @VisibleForTesting
  val newImagePanelSingle = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
  @VisibleForTesting
  val diffImagePanelSingle = ImageWithToolbarPanel(ScreenshotViewType.DIFF, showToolbar = true, showTitle = true)
  @VisibleForTesting
  val refImagePanelSingle = ImageWithToolbarPanel(ScreenshotViewType.REFERENCE, showToolbar = true, showTitle = true)

  private val contentPanel = JPanel(CardLayout())

  @VisibleForTesting
  var selectedTab by mutableStateOf(ScreenshotViewType.ALL.displayText)

  private val composeTabBar = ComposePanel().apply {
    var componentWidth by mutableIntStateOf(0)

    setContent {
      SwingBridgeTheme {
        val buttonData = remember(selectedTab) {
          ScreenshotViewType.values().map { viewId ->
            SegmentedControlButtonData(
              selected = viewId.displayText == selectedTab,
              content = { _ -> Text(viewId.displayText) },
              onSelect = { selectTab(viewId.displayText) },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.Center
        ) {
          SegmentedControl(buttons = buttonData, enabled = true)
        }
      }
    }
  }


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
  fun selectTab(tab: String) {
    selectedTab = tab
    val cardLayout = contentPanel.layout as CardLayout
    cardLayout.show(contentPanel, tab)
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

  @VisibleForTesting
  val commonToggleChessboardAction =
    object : ToggleAction("Chessboard", "Toggle Chessboard Background", IconLoader.getIcon("/org/intellij/images/icons/expui/chessboard.svg", ScreenshotResultView::class.java)) {
      override fun isSelected(e: AnActionEvent): Boolean = multiViewPanels.firstOrNull()?.isChessboardVisible() ?: false
      override fun setSelected(e: AnActionEvent, state: Boolean) = multiViewPanels.forEach { it.setChessboardVisible(state) }
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

    contentPanel.add(allTabPanel, ScreenshotViewType.ALL.displayText)
    contentPanel.add(newImagePanelSingle, ScreenshotViewType.NEW.displayText)
    contentPanel.add(diffImagePanelSingle, ScreenshotViewType.DIFF.displayText)
    contentPanel.add(refImagePanelSingle, ScreenshotViewType.REFERENCE.displayText)

    myView.add(contentPanel, BorderLayout.CENTER)
    myView.add(composeTabBar, BorderLayout.SOUTH)
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
}
