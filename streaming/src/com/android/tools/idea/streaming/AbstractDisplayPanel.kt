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
package com.android.tools.idea.streaming

import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.streaming.emulator.NotificationHolderPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
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
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.border.Border
import javax.swing.plaf.ScrollBarUI

/**
 * Represents a single display of an Android device.
 */
@Suppress("LeakingThis") // Passing "this" to Disposer in constructor is safe because the dispose method is final.
abstract class AbstractDisplayPanel<T : AbstractDisplayView>(
  disposableParent: Disposable,
  zoomToolbarVisible: Boolean,
) : BorderLayoutPanel(), Disposable {

  private val scrollPane: JScrollPane
  private val centerPanel: NotificationHolderPanel
  private var floatingToolbar: JComponent
  protected val loadingPanel: StreamingLoadingPanel
  private var _displayView: T? = null
  var displayView: T
    get() =
      _displayView ?: throw IllegalStateException("displayView is not initialized")
    protected set(view) {
      _displayView = view
      scrollPane.setViewportView(displayView)
    }

  val displayId
    get() = displayView.displayId

  var zoomToolbarVisible: Boolean
    get() = floatingToolbar.isVisible
    set(value) { floatingToolbar.isVisible = value }

  internal var zoomScrollState: ZoomScrollState
    get() = ZoomScrollState(scrollPane.viewport.viewPosition, displayView.explicitlySetPreferredSize)
    set(value) {
      if (value.preferredViewSize != null) {
        displayView.preferredSize = value.preferredViewSize
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

    scrollPane = MyScrollPane()

    val layeredPane = JLayeredPane().apply {
      layout = LayeredPaneLayoutManager()
      isFocusable = true
      setLayer(zoomControlsLayerPane, JLayeredPane.PALETTE_LAYER)
      setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)

      add(zoomControlsLayerPane, BorderLayout.CENTER)
      add(scrollPane, BorderLayout.CENTER)
    }

    floatingToolbar = ZoomToolbarProvider.createToolbar(this, this)
    floatingToolbar.isVisible = zoomToolbarVisible
    zoomControlsLayerPane.add(floatingToolbar, BorderLayout.EAST)

    loadingPanel = StreamingLoadingPanel(this)
    loadingPanel.add(layeredPane, BorderLayout.CENTER)

    centerPanel = NotificationHolderPanel(loadingPanel)
    addToCenter(centerPanel)
  }

  fun showLongRunningOperationIndicator(text: String) {
    loadingPanel.setLoadingText(text)
    loadingPanel.startLoading()
  }

  final override fun dispose() {
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

    override fun createVerticalScrollBar(): JScrollBar =
      MyScrollBar(Adjustable.VERTICAL)

    override fun createHorizontalScrollBar(): JScrollBar =
      MyScrollBar(Adjustable.HORIZONTAL)

    override fun setBorder(border: Border?) {
      // Don't allow borders to be set by the UI framework.
    }

    init {
      setupCorners()
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
  }

  private class MyScrollBar(@AdjustableOrientation orientation: Int) : JBScrollBar(orientation) {

    private var persistentUI: ScrollBarUI? = null

    override fun setUI(ui: ScrollBarUI) {
      if (persistentUI == null) {
        persistentUI = ui
      }
      super.setUI(persistentUI)
      isOpaque = false
    }

    override fun getUnitIncrement(direction: Int): Int = 5

    override fun getBlockIncrement(direction: Int): Int = 1

    init {
      isOpaque = false
    }
  }
}
