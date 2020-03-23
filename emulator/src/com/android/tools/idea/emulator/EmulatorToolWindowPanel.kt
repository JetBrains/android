/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.emulator.EmulatorConstants.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.emulator.EmulatorConstants.EMULATOR_TOOLBAR_ID
import com.android.tools.idea.emulator.EmulatorConstants.EMULATOR_VIEW_KEY
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.intellij.lang.annotations.JdkConstants.AdjustableOrientation
import java.awt.Adjustable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.plaf.ScrollBarUI

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
internal class EmulatorToolWindowPanel(private val emulator: EmulatorController) : BorderLayoutPanel(), DataProvider {
  private val actionToolbar: ActionToolbar
  private var emulatorView: EmulatorView? = null
  private val scrollPane: JScrollPane
  private val layeredPane: JLayeredPane
  private val zoomControlsLayerPane: JPanel
  private var contentDisposable: Disposable? = null
  private var floatingToolbar: JComponent? = null

  val id
    get() = emulator.emulatorId

  val title
    get() = emulator.emulatorId.avdName

  val icon
    get() = ICON

  val component: JComponent
    get() = this

  var zoomToolbarIsVisible = false
    set(value) {
      field = value
      floatingToolbar?.let { it.isVisible = value }
    }

  init {
    val toolbarActionGroup = DefaultActionGroup(createToolbarActions())
    actionToolbar = ActionManager.getInstance().createActionToolbar(EMULATOR_TOOLBAR_ID, toolbarActionGroup, isToolbarHorizontal)

    zoomControlsLayerPane = JPanel()
    zoomControlsLayerPane.layout = BorderLayout()
    zoomControlsLayerPane.border = JBUI.Borders.empty(UIUtil.getScrollBarWidth())
    zoomControlsLayerPane.isOpaque = false
    zoomControlsLayerPane.isFocusable = true

    scrollPane = MyScrollPane()
    scrollPane.border = null
    scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
    scrollPane.viewport.background = background

    layeredPane = JLayeredPane()
    layeredPane.layout = LayeredPaneLayoutManager()
    layeredPane.isFocusable = true
    layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
    layeredPane.setLayer(zoomControlsLayerPane, JLayeredPane.PALETTE_LAYER)

    layeredPane.add(zoomControlsLayerPane, BorderLayout.CENTER)
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    addToolbar()
    addToCenter(layeredPane)
  }

  private fun addToolbar() {
    if (isToolbarHorizontal) {
      actionToolbar.setOrientation(SwingConstants.HORIZONTAL)
      layeredPane.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(actionToolbar.component)
    }
    else {
      actionToolbar.setOrientation(SwingConstants.VERTICAL)
      layeredPane.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(actionToolbar.component)
    }
  }

  fun setCropFrame(value: Boolean) {
    emulatorView?.cropFrame = value
  }

  fun createContent(cropSkin: Boolean) {
    try {
      val disposable = Disposer.newDisposable()
      val toolbar = EmulatorZoomToolbar.getToolbar(this, disposable)
      toolbar.isVisible = zoomToolbarIsVisible
      floatingToolbar = toolbar
      zoomControlsLayerPane.add(toolbar, BorderLayout.EAST)
      contentDisposable = disposable
      emulatorView = EmulatorView(emulator, cropSkin)
      scrollPane.setViewportView(emulatorView)
      actionToolbar.setTargetComponent(emulatorView)
      layeredPane.repaint()
    }
    catch (e: Exception) {
      val label = "Unable to load emulator view: $e"
      add(JLabel(label), BorderLayout.CENTER)
    }
  }

  fun destroyContent() {
    emulatorView = null
    floatingToolbar = null
    contentDisposable?.let { Disposer.dispose(it) }
    zoomControlsLayerPane.removeAll()
    actionToolbar.setTargetComponent(null)
    scrollPane.setViewportView(null)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
      else -> null
    }
  }

  private fun createToolbarActions() =
      listOf(CustomActionsSchema.getInstance().getCorrectedAction(EMULATOR_TOOLBAR_ID)!!)

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

  companion object {
    @JvmStatic
    private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    private const val isToolbarHorizontal = true
  }
}
