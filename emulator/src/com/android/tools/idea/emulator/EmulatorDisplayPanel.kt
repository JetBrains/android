/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.intellij.lang.annotations.JdkConstants.AdjustableOrientation
import java.awt.Adjustable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.plaf.ScrollBarUI

/**
 * Represents a single Emulator display.
 */
class EmulatorDisplayPanel(
  disposableParent: Disposable,
  val emulator: EmulatorController,
  displayId: Int,
  displaySize: Dimension?,
  zoomToolbarVisible: Boolean,
  deviceFrameVisible: Boolean = false,
) : BorderLayoutPanel(), DataProvider, Disposable {

  val emulatorView: EmulatorView
  private val scrollPane: JScrollPane
  private val centerPanel: BorderLayoutPanel
  private var floatingToolbar: JComponent

  val component: JComponent
    get() = this

  val displayId
    get() = emulatorView.displayId

  var zoomToolbarVisible: Boolean
    get() = floatingToolbar.isVisible
    set(value) { floatingToolbar.isVisible = value }

  internal var zoomScrollState: ZoomScrollState
    get() = ZoomScrollState(scrollPane.viewport.viewPosition, emulatorView.explicitlySetPreferredSize)
    set(value) {
      if (value.preferredViewSize != null) {
        emulatorView.preferredSize = value.preferredViewSize
        scrollPane.viewport.viewPosition = value.viewPosition
      }
    }

  init {
    Disposer.register(disposableParent, this)

    background = primaryPanelBackground

    val zoomControlsLayerPane = JPanel().apply {
      layout = BorderLayout()
      border = JBUI.Borders.empty(UIUtil.getScrollBarWidth())
      isOpaque = false
      isFocusable = true
    }

    scrollPane = MyScrollPane().apply {
      border = null
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
      viewport.background = background
      viewport.addChangeListener {
        val view = viewport.view
        // Remove the explicitly set preferred view size if it does not exceed the viewport size.
        if (view != null && view.isPreferredSizeSet &&
            view.preferredSize.width <= viewport.width && view.preferredSize.height <= viewport.height) {
          view.preferredSize = null
        }
      }
    }

    val layeredPane = JLayeredPane().apply {
      layout = LayeredPaneLayoutManager()
      isFocusable = true
      setLayer(zoomControlsLayerPane, JLayeredPane.PALETTE_LAYER)
      setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)

      add(zoomControlsLayerPane, BorderLayout.CENTER)
      add(scrollPane, BorderLayout.CENTER)
    }

    centerPanel = NotificationHolderPanel()

    emulatorView = EmulatorView(this, emulator, displayId, displaySize, deviceFrameVisible)

    floatingToolbar = EmulatorZoomToolbarProvider.createToolbar(this, this)
    floatingToolbar.isVisible = zoomToolbarVisible
    zoomControlsLayerPane.add(floatingToolbar, BorderLayout.EAST)

    scrollPane.setViewportView(emulatorView)

    addToCenter(centerPanel)

    val loadingPanel = EmulatorLoadingPanel(this)
    loadingPanel.add(layeredPane, BorderLayout.CENTER)
    centerPanel.addToCenter(loadingPanel)

    if (displayId == PRIMARY_DISPLAY_ID) {
      loadingPanel.setLoadingText("Connecting to the Emulator")
      loadingPanel.startLoading() // The stopLoading method is called by EmulatorView after the gRPC connection is established.
    }

    loadingPanel.repaint()
  }

  override fun dispose() {
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulatorView.emulator
      EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
      else -> null
    }
  }

  /**
   * Zoom and scroll state of the panel.
   */
  class ZoomScrollState(val viewPosition: Point, val preferredViewSize: Dimension?)

  private class LayeredPaneLayoutManager : LayoutManager {

    override fun layoutContainer(target: Container) {
      val insets: Insets = target.insets
      val top = insets.top
      val bottom = target.height - insets.bottom
      val left = insets.left
      val right = target.width - insets.right

      for (child in target.components) {
        child.setBounds(left, top, right - left, bottom - top)
      }
    }

    // Request all available space.
    override fun preferredLayoutSize(parent: Container): Dimension =
      if (parent.isPreferredSizeSet) parent.preferredSize else Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)
    override fun addLayoutComponent(name: String?, comp: Component?) {}
    override fun removeLayoutComponent(comp: Component?) {}
  }

  private class MyScrollPane : JBScrollPane(0) {

    override fun createVerticalScrollBar(): JScrollBar {
      return MyScrollBar(Adjustable.VERTICAL)
    }

    override fun createHorizontalScrollBar(): JScrollBar {
      return MyScrollBar(Adjustable.HORIZONTAL)
    }

    init {
      setupCorners()
    }
  }

  private class MyScrollBar(
    @AdjustableOrientation orientation: Int
  ) : JBScrollBar(orientation), IdeGlassPane.TopComponent {

    private var persistentUI: ScrollBarUI? = null

    override fun canBePreprocessed(event: MouseEvent): Boolean {
      return JBScrollPane.canBePreprocessed(event, this)
    }

    override fun setUI(ui: ScrollBarUI) {
      if (persistentUI == null) {
        persistentUI = ui
      }
      super.setUI(persistentUI)
      isOpaque = false
    }

    override fun getUnitIncrement(direction: Int): Int {
      return 5
    }

    override fun getBlockIncrement(direction: Int): Int {
      return 1
    }

    init {
      isOpaque = false
    }
  }
}
