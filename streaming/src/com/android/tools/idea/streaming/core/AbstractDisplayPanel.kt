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
package com.android.tools.idea.streaming.core

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.actions.ZoomActualAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.actions.FloatingXrToolbarState
import com.android.tools.idea.streaming.actions.ZoomLevelIndicator
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
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
import javax.swing.JViewport
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.plaf.ScrollBarUI
import org.intellij.lang.annotations.JdkConstants.AdjustableOrientation

/**
 * Represents a single display of an Android device.
 */
@Suppress("LeakingThis") // Passing "this" to Disposer in constructor is safe because the dispose method is final.
abstract class AbstractDisplayPanel<T : AbstractDisplayView>(
  disposableParent: Disposable,
  zoomToolbarVisible: Boolean,
) : BorderLayoutPanel(), UiDataProvider, Disposable {

  private val scrollPane: JScrollPane
  private val floatingToolbarLayerPane: JComponent
  private var zoomToolbar: JComponent? = null
  private var xrNavigationToolbar: JComponent? = null
  protected val loadingPanel: StreamingLoadingPanel
  private var _displayView: T? = null
  var displayView: T
    get() = _displayView ?: throw IllegalStateException("displayView is not initialized")
    protected set(view) {
      _displayView = view
      scrollPane.setViewportView(displayView)
    }

  val displayId
    get() = displayView.displayId

  var zoomToolbarVisible: Boolean = zoomToolbarVisible
    set(value) {
      field = value
      zoomToolbar?.isVisible = value
    }

  internal var zoomScrollState: ZoomScrollState
    get() = ZoomScrollState(scrollPane.viewport.viewPosition, displayView.explicitlySetPreferredSize)
    set(value) {
      if (value.preferredViewSize != null) {
        displayView.preferredSize = value.preferredViewSize
        scrollPane.viewport.viewPosition = value.viewPosition
      }
    }
  protected abstract val deviceType: DeviceType

  init {
    Disposer.register(disposableParent, this)

    ApplicationManager.getApplication().messageBus.connect(this).subscribe(
        FloatingXrToolbarState.Listener.TOPIC,
        object : FloatingXrToolbarState.Listener {
          override fun floatingXrToolbarStateChanged(enabled: Boolean) {
            xrNavigationToolbar?.isVisible = enabled
          }
        })

    background = primaryPanelBackground

    scrollPane = MyScrollPane()

    floatingToolbarLayerPane = JPanel().apply {
      val layoutDirection = when (StudioFlags.RUNNING_DEVICES_COLLAPSIBLE_FLOATING_TOOLBARS.get()) {
        true -> DirectionalFlowLayout.Direction.BOTTOM_TO_TOP
        false -> DirectionalFlowLayout.Direction.RIGHT_TO_LEFT
      }
      layout = DirectionalFlowLayout(layoutDirection, gap = JBUI.scale(6))
      val scrollBarWidth = scrollPane.verticalScrollBar.preferredWidth + 1
      @Suppress("UseDPIAwareBorders") // scrollBarWidth is scaled already.
      border = EmptyBorder(scrollBarWidth, scrollBarWidth, scrollBarWidth, scrollBarWidth)
      isOpaque = false
      isFocusable = true
    }

    val layeredPane = JLayeredPane().apply {
      layout = LayeredPaneLayoutManager()
      isFocusable = true
      setLayer(floatingToolbarLayerPane, JLayeredPane.PALETTE_LAYER)
      setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)

      add(floatingToolbarLayerPane, BorderLayout.CENTER)
      add(scrollPane, BorderLayout.CENTER)
    }

    loadingPanel = StreamingLoadingPanel(this)
    loadingPanel.add(layeredPane, BorderLayout.CENTER)
    addToCenter(loadingPanel)
  }

  protected fun createFloatingToolbar() {
    floatingToolbarLayerPane.removeAll()
    when (deviceType) {
      DeviceType.XR_HEADSET -> createXrNavigationToolbar()
      else -> createZoomToolbar()
    }
  }

  private fun createZoomToolbar() {
    if (StudioFlags.RUNNING_DEVICES_COLLAPSIBLE_FLOATING_TOOLBARS.get()) {
      val zoomGroup = DefaultActionGroup().apply {
        add(ZoomInAction.getInstance())
        add(ZoomOutAction.getInstance())
        add(ZoomActualAction.getInstance())
        add(ZoomToFitAction.getInstance())
        add(Separator())
        add(ZoomLevelIndicator())
        add(FloatingToolbarContainer.CollapserAction())
      }
      val toolbar = FloatingToolbarContainer(
        horizontal = true,
        inactiveAlpha = 0.8,
        collapsedStateSelector = { it.action is ZoomLevelIndicator }
      ).apply {
        addToolbar("ZoomToolbar", zoomGroup)
        isVisible = zoomToolbarVisible
        setTargetComponent(displayView)
      }
      floatingToolbarLayerPane.add(toolbar, BorderLayout.SOUTH)
      zoomToolbar = toolbar
    }
    else {
      val toolbar = ZoomToolbarProvider.createToolbar(this, this)
      toolbar.isVisible = zoomToolbarVisible
      floatingToolbarLayerPane.add(toolbar)
      zoomToolbar = toolbar
    }
  }

  private fun createXrNavigationToolbar() {
    if (StudioFlags.RUNNING_DEVICES_COLLAPSIBLE_FLOATING_TOOLBARS.get()) {
      val toolbar = FloatingToolbarContainer(horizontal = true, inactiveAlpha = 0.8, collapsedStateSelector = { it.isSelected },
                                             initiallyActive = true).apply {
        val group = DefaultActionGroup()
        val actionManager = ActionManager.getInstance()
        val inputModeGroup = actionManager.getAction("android.streaming.xr.input.mode.group") as? ActionGroup
        if (inputModeGroup != null) {
          group.add(inputModeGroup)
        }
        group.add(Separator.getInstance())
        val recenterGroup = actionManager.getAction("android.streaming.xr.recenter.group") as? ActionGroup
        if (recenterGroup != null) {
          group.add(recenterGroup)
        }
        group.add(FloatingToolbarContainer.CollapserAction())
        addToolbar("XrNavigationToolbar", group)
      }

      toolbar.setTargetComponent(displayView)
      toolbar.isVisible = service<FloatingXrToolbarState>().floatingXrToolbarEnabled
      floatingToolbarLayerPane.add(toolbar)
      xrNavigationToolbar = toolbar
    }
    else {
      val toolbar = FloatingToolbarContainer(horizontal = false, inactiveAlpha = 0.7, activateOnHover = true).apply {
        val actionManager = ActionManager.getInstance()
        val inputModeGroup = actionManager.getAction("android.streaming.xr.input.mode.group") as? ActionGroup
        if (inputModeGroup != null) {
          addToolbar("FloatingToolbar", inputModeGroup)
        }

        val recenterGroup = actionManager.getAction("android.streaming.xr.recenter.group") as? ActionGroup
        if (recenterGroup != null) {
          addToolbar("FloatingToolbar", recenterGroup)
        }
      }
      toolbar.setTargetComponent(displayView)
      toolbar.isVisible = service<FloatingXrToolbarState>().floatingXrToolbarEnabled
      floatingToolbarLayerPane.add(toolbar, BorderLayout.EAST)
      xrNavigationToolbar = toolbar
    }
  }

  final override fun dispose() {
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val sizeChanged = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (sizeChanged && zoomToolbarVisible) {
      ActivityTracker.getInstance().inc() // Trigger toolbar update.
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DISPLAY_ID_KEY] = displayId
    sink[DISPLAY_VIEW_KEY] = displayView
    sink[ZOOMABLE_KEY] = displayView
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

    init {
      setupCorners()
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
      viewport.background = background
    }

    override fun createViewport(): JViewport =
        CenterAnchoredViewport()

    override fun createVerticalScrollBar(): JScrollBar =
        MyScrollBar(Adjustable.VERTICAL)

    override fun createHorizontalScrollBar(): JScrollBar =
        MyScrollBar(Adjustable.HORIZONTAL)

    override fun setBorder(border: Border?) {
      // Don't allow borders to be set by the UI framework.
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      val oldWidth = this.width
      val oldHeight = this.height
      super.setBounds(x, y, width, height)
      val view = viewport.view
      val viewPreferredSize = view.preferredSize
      if ((
            viewPreferredSize.width <= oldWidth && viewPreferredSize.height <= oldHeight &&
            (viewPreferredSize.width >= width || viewPreferredSize.height >= height)
          ) ||
          (
            (viewPreferredSize.width >= oldWidth || viewPreferredSize.height >= oldHeight) &&
            viewPreferredSize.width <= width && width < viewPreferredSize.width * 2 &&
            viewPreferredSize.height <= height && height < viewPreferredSize.height * 2
          )) {
        // The size of the scroll pane crossed the point where it matched the preferred size of the view.
        // Reset the preferred size of the view so that it starts resizing with the scroll pane.
        view.preferredSize = null
      }
    }
  }

  private class MyScrollBar(@AdjustableOrientation orientation: Int) : JBScrollBar(orientation) {

    private var persistentUI: ScrollBarUI? = null

    init {
      isOpaque = false
    }

    override fun setUI(ui: ScrollBarUI) {
      if (persistentUI == null) {
        persistentUI = ui
      }
      super.setUI(persistentUI)
      isOpaque = false
    }

    override fun setVisible(isVisible: Boolean) {
      super.setVisible(isVisible)
      isOpaque = isVisible
    }

    override fun getUnitIncrement(direction: Int): Int = 5

    override fun getBlockIncrement(direction: Int): Int = 1
  }
}
