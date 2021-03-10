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

import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.DisplayConfigurations
import com.android.emulator.control.ExtendedControlsStatus
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.util.ActionToolbarUtil.makeToolbarNavigable
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.emulator.actions.findManageSnapshotDialog
import com.android.tools.idea.emulator.actions.showExtendedControls
import com.android.tools.idea.emulator.actions.showManageSnapshotsDialog
import com.android.tools.idea.flags.StudioFlags
import com.google.common.collect.ImmutableList
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
private const val isToolbarHorizontal = true

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
class EmulatorToolWindowPanel(
  private val project: Project,
  val emulator: EmulatorController
) : BorderLayoutPanel(), DataProvider {

  private val mainToolbar: ActionToolbar
  private val centerPanel = BorderLayoutPanel()
  private val displayPanels = Int2ObjectRBTreeMap<EmulatorDisplayPanel>()
  private val displayConfigurator = DisplayConfigurator()
  private var contentDisposable: Disposable? = null

  private var primaryEmulatorView: EmulatorView? = null

  val id
    get() = emulator.emulatorId

  val title
    get() = emulator.emulatorId.avdName

  val icon
    get() = ICON

  val component: JComponent
    get() = this

  var zoomToolbarVisible = false
    set(value) {
      field = value
      for (panel in displayPanels.values) {
        panel.zoomToolbarVisible = value
      }
    }

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  @TestOnly
  var lastUiState: UiState? = null

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(EMULATOR_MAIN_TOOLBAR_ID, isToolbarHorizontal)

    addToCenter(centerPanel)
    addToolbar()
  }

  private fun addToolbar() {
    if (isToolbarHorizontal) {
      mainToolbar.setOrientation(SwingConstants.HORIZONTAL)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(mainToolbar.component)
    }
    else {
      mainToolbar.setOrientation(SwingConstants.VERTICAL)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(mainToolbar.component)
    }
  }

  fun getPreferredFocusableComponent(): JComponent {
    return primaryEmulatorView ?: this
  }

  fun setDeviceFrameVisible(visible: Boolean) {
    primaryEmulatorView?.deviceFrameVisible = visible
  }

  /**
   * Populates the emulator panel with content.
   */
  fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null) {
    try {
      lastUiState = null
      val disposable = Disposer.newDisposable()
      contentDisposable = disposable

      val displayPanel = EmulatorDisplayPanel(disposable, emulator, PRIMARY_DISPLAY_ID, null, zoomToolbarVisible, deviceFrameVisible)
      displayPanels[displayPanel.displayId] = displayPanel
      val emulatorView = displayPanel.emulatorView
      primaryEmulatorView = emulatorView
      mainToolbar.setTargetComponent(emulatorView)
      installFileDropHandler(this, emulatorView, project)
      emulatorView.addDisplayConfigurationListener(displayConfigurator)
      emulator.addConnectionStateListener(displayConfigurator)

      val uiState: UiState = savedUiState ?: UiState()
      val displayLayout = uiState.panelLayout
      if (uiState.displayDescriptors.size <= 1 || displayLayout == null) {
        centerPanel.addToCenter(displayPanel)
      }
      else {
        displayConfigurator.rebuildLayout(displayLayout, uiState.displayDescriptors)
      }

      val zoomScrollState = uiState.zoomScrollState
      for (panel in displayPanels.values) {
        zoomScrollState[panel.displayId]?.let { panel.zoomScrollState = it }
      }

      if (connected) {
        displayConfigurator.refreshDisplayConfiguration()

        if (uiState.manageSnapshotsDialogShown) {
          showManageSnapshotsDialog(emulatorView, project)
        }
        if (uiState.extendedControlsShown) {
          showExtendedControls(emulator)
        }
      }
    }
    catch (e: Exception) {
      val label = "Unable to create emulator view: $e"
      add(JLabel(label), BorderLayout.CENTER)
    }
  }

  /**
   * Destroys content of the emulator panel and returns its state for later recreation.
   */
  fun destroyContent(): UiState {
    val uiState = UiState()
    uiState.displayDescriptors = ImmutableList.copyOf(displayConfigurator.displayDescriptors)
    if (displayConfigurator.displayDescriptors.size > 1) {
      uiState.panelLayout = displayConfigurator.getPanelLayout(centerPanel.getComponent(0))
    }

    for (panel in displayPanels.values) {
      uiState.zoomScrollState[panel.displayId] = panel.zoomScrollState
    }

    val manageSnapshotsDialog = primaryEmulatorView?.let { findManageSnapshotDialog(it) }
    uiState.manageSnapshotsDialogShown = manageSnapshotsDialog != null
    manageSnapshotsDialog?.close(DialogWrapper.CLOSE_EXIT_CODE)

    if (StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.get() && connected) {
      emulator.closeExtendedControls(object: EmptyStreamObserver<ExtendedControlsStatus>() {
        override fun onNext(response: ExtendedControlsStatus) {
          EventQueue.invokeLater {
            uiState.extendedControlsShown = response.visibilityChanged
          }
        }
      })
    }

    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    centerPanel.removeAll()
    displayPanels.clear()
    primaryEmulatorView = null
    mainToolbar.setTargetComponent(null)
    lastUiState = uiState
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> primaryEmulatorView
      else -> null
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
    makeToolbarNavigable(toolbar)
    return toolbar
  }

  private inner class DisplayConfigurator : DisplayConfigurationListener, ConnectionStateListener {

    var displayDescriptors = emptyList<DisplayDescriptor>()

    override fun displayConfigurationChanged() {
      refreshDisplayConfiguration()
    }

    override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
      if (connectionState == ConnectionState.CONNECTED) {
        refreshDisplayConfiguration()
      }
    }

    fun refreshDisplayConfiguration() {
      emulator.getDisplayConfigurations(object: EmptyStreamObserver<DisplayConfigurations>() {
        override fun onNext(response: DisplayConfigurations) {
          EventQueue.invokeLater {
            displayConfigurationReceived(response.displaysList)
          }
        }
      })
    }

    private fun displayConfigurationReceived(displayConfigs: List<DisplayConfiguration>) {
      val primaryDisplayView = primaryEmulatorView ?: return
      val newDisplays = getDisplayDescriptors(primaryDisplayView, displayConfigs)
      if (newDisplays.size == 1 && displayDescriptors.size <= 1 || newDisplays == displayDescriptors) {
        return
      }

      displayPanels.int2ObjectEntrySet().removeIf { (displayId, displayPanel) ->
          if (!newDisplays.any { it.displayId == displayId} ) {
            Disposer.dispose(displayPanel)
            true
          }
          else {
            false
          }
      }
      val layoutRoot = computeBestLayout(centerPanel.sizeWithoutInsets, newDisplays.map { it.size })
      rebuildLayout(layoutRoot, newDisplays)
    }

    fun rebuildLayout(layoutRoot: LayoutNode, newDisplays: List<DisplayDescriptor>) {
      displayDescriptors = newDisplays
      centerPanel.removeAll()
      centerPanel.addToCenter(buildLayout(layoutRoot))
    }

    private fun buildLayout(layoutNode: LayoutNode): JPanel {
      return when (layoutNode) {
        is LeafNode -> {
          val display = displayDescriptors[layoutNode.rectangleIndex]
          val displayId = display.displayId
          displayPanels.computeIfAbsent(displayId) {
            assert(displayId != PRIMARY_DISPLAY_ID)
            EmulatorDisplayPanel(contentDisposable!!, emulator, displayId, display.size, zoomToolbarVisible)
          }
        }
        is SplitNode -> {
          EmulatorSplitPanel(layoutNode).apply {
            firstComponent = buildLayout(layoutNode.firstChild)
            secondComponent = buildLayout(layoutNode.secondChild)
          }
        }
      }
    }

    private fun getDisplayDescriptors(emulatorView: EmulatorView, displays: List<DisplayConfiguration>): List<DisplayDescriptor> {
      return displays
        .map {
          if (it.display == PRIMARY_DISPLAY_ID) {
            DisplayDescriptor(PRIMARY_DISPLAY_ID, emulatorView.displaySizeWithFrame)
          }
          else {
            DisplayDescriptor(it)
          }
        }
        .sorted()
    }

    fun getPanelLayout(panel: Component): LayoutNode {
      return when (panel) {
        is EmulatorDisplayPanel -> {
          val descriptorIndex = displayIdToDescriptorIndex(panel.displayId)
          check(descriptorIndex >= 0)
          LeafNode(descriptorIndex, panel.size)
        }
        is EmulatorSplitPanel -> {
          val splitType = if (panel.isVerticalSplit) SplitType.VERTICAL else SplitType.HORIZONTAL
          val firstNode = getPanelLayout(panel.firstComponent)
          val secondNode = getPanelLayout(panel.secondComponent)
          SplitNode(splitType, firstNode, secondNode)
        }
        else -> {
          throw IllegalArgumentException("Unexpected panel type: ${panel.javaClass.name}")
        }
      }
    }

    private fun displayIdToDescriptorIndex(displayId: Int): Int {
      for ((index, descriptor) in displayDescriptors.withIndex()) {
        if (descriptor.displayId == displayId) {
          return index
        }
      }
      return -1
    }
  }

  data class DisplayDescriptor(val displayId: Int, val size: Dimension) : Comparable<DisplayDescriptor> {

    constructor(displayConfig: DisplayConfiguration) : this(displayConfig.display, Dimension(displayConfig.width, displayConfig.height))

    override fun compareTo(other: DisplayDescriptor): Int {
      return displayId - other.displayId
    }
  }

  class UiState {
    var manageSnapshotsDialogShown = false
    var extendedControlsShown = false
    var displayDescriptors = emptyList<DisplayDescriptor>()
    var panelLayout: LayoutNode? = null
    val zoomScrollState = Int2ObjectRBTreeMap<EmulatorDisplayPanel.ZoomScrollState>()
  }
}
