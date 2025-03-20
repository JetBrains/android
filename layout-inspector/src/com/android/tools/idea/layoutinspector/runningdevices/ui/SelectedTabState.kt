/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.dataProviderForLayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel.SelectionListener
import com.android.tools.idea.layoutinspector.properties.DimensionUnitAction
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.SPLITTER_KEY
import com.android.tools.idea.layoutinspector.runningdevices.actions.GearAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.HorizontalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.LeftVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.RightVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapHorizontalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapLeftVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapRightVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.actions.VerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.android.tools.idea.layoutinspector.ui.toolbar.createEmbeddedLayoutInspectorToolbar
import com.android.tools.idea.streaming.core.DeviceId
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import org.jetbrains.annotations.TestOnly

private const val WORKBENCH_NAME = "Layout Inspector"
@VisibleForTesting
const val UI_CONFIGURATION_KEY =
  "com.android.tools.idea.layoutinspector.runningdevices.ui.uiconfigkey"

private val logger = Logger.getInstance(SelectedTabState::class.java)

/**
 * Represents the state of the selected tab.
 *
 * @param deviceId The id of selected tab.
 * @param tabComponents The components of the selected tab.
 */
@UiThread
data class SelectedTabState(
  val project: Project,
  val deviceId: DeviceId,
  val tabComponents: TabComponents,
  val layoutInspector: LayoutInspector,
  val rendererPanel: LayoutInspectorRenderer,
) : Disposable {

  private var uiConfig = UiConfig.HORIZONTAL
  private var wrapLogic: WrapLogic? = null

  init {
    Disposer.register(tabComponents, this)

    // Try to restore UI config
    val uiConfigString = PropertiesComponent.getInstance().getValue(UI_CONFIGURATION_KEY)
    uiConfig = uiConfigString?.let { UiConfig.valueOf(uiConfigString) } ?: UiConfig.HORIZONTAL

    val layoutInspectorProvider = dataProviderForLayoutInspector(layoutInspector)
    DataManager.registerDataProvider(rendererPanel, layoutInspectorProvider)
    Disposer.register(this) { DataManager.removeDataProvider(rendererPanel) }
  }

  @TestOnly
  fun enableLayoutInspector(uiConfig: UiConfig) {
    this.uiConfig = uiConfig
    enableLayoutInspector()
  }

  fun enableLayoutInspector() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    wrapUi(uiConfig)
    tabComponents.displayView.add(rendererPanel)

    layoutInspector.inspectorModel.addSelectionListener(selectionChangedListener)
    layoutInspector.processModel?.addSelectedProcessListeners(
      EdtExecutorService.getInstance(),
      selectedProcessListener,
    )

    logger.debug("Embedded Layout Inspector successfully enabled.")
  }

  /** Wrap the RD tab by injecting Embedded Layout Inspector UI. */
  private fun wrapUi(uiConfig: UiConfig) {
    PropertiesComponent.getInstance().setValue(UI_CONFIGURATION_KEY, uiConfig.name)

    wrapLogic =
      WrapLogic(this, tabComponents.tabContentPanel, tabComponents.tabContentPanelContainer)

    wrapLogic?.wrapComponent { disposable, centerPanel ->
      val inspectorPanel = BorderLayoutPanel()

      val mainPanel =
        when (uiConfig) {
          UiConfig.HORIZONTAL,
          UiConfig.HORIZONTAL_SWAP -> {
            // Create a vertical split panel containing the device view at the top and the tool
            // windows at the bottom.
            val toolsPanel = createToolsPanel(disposable, uiConfig, inspectorPanel, null)
            val splitPanel =
              OnePixelSplitter(true, SPLITTER_KEY, 0.65f).apply {
                firstComponent = centerPanel
                secondComponent = toolsPanel
                setBlindZone { JBUI.insets(0, 1) }
              }
            splitPanel
          }
          UiConfig.VERTICAL,
          UiConfig.VERTICAL_SWAP,
          UiConfig.LEFT_VERTICAL,
          UiConfig.LEFT_VERTICAL_SWAP,
          UiConfig.RIGHT_VERTICAL,
          UiConfig.RIGHT_VERTICAL_SWAP -> {
            // Create a workbench containing the panels on the side and the device view at the
            // center.
            createToolsPanel(disposable, uiConfig, inspectorPanel, centerPanel)
          }
        }

      val inspectorBanner = InspectorBanner(disposable, layoutInspector.notificationModel)
      inspectorPanel.add(inspectorBanner, BorderLayout.NORTH)
      inspectorPanel.add(mainPanel, BorderLayout.CENTER)
      inspectorPanel
    }
  }

  /** Unwrap the RD tab by removing Embedded Layout Inspector UI. */
  private fun unwrapUi() {
    wrapLogic?.let { Disposer.dispose(it) }
    wrapLogic = null
  }

  /**
   * Create a panel containing a toolbar and the workbench with the side panels and optionally
   * [centerPanel] as the center panel.
   */
  private fun createToolsPanel(
    disposable: Disposable,
    uiConfig: UiConfig,
    rootPanel: JComponent,
    centerPanel: JComponent? = null,
  ): JPanel {
    val toolsPanel = BorderLayoutPanel()

    val toolbar = createToolbar(disposable, toolsPanel, rootPanel)

    val workBench =
      createLayoutInspectorWorkbench(project, disposable, layoutInspector, uiConfig, centerPanel)
    workBench.isFocusCycleRoot = false

    val layoutInspectorProvider = dataProviderForLayoutInspector(layoutInspector)
    DataManager.registerDataProvider(toolsPanel, layoutInspectorProvider)
    DataManager.registerDataProvider(toolbar, layoutInspectorProvider)
    DataManager.registerDataProvider(workBench, layoutInspectorProvider)

    Disposer.register(disposable) {
      DataManager.removeDataProvider(toolsPanel)
      DataManager.removeDataProvider(toolbar)
      DataManager.removeDataProvider(workBench)
    }

    toolsPanel.add(toolbar, BorderLayout.NORTH)
    toolsPanel.add(workBench, BorderLayout.CENTER)
    workBench.component.border = JBUI.Borders.customLineTop(JBColor.border())
    return toolsPanel
  }

  private fun createToolbar(
    parentDisposable: Disposable,
    targetComponent: JComponent,
    rootComponent: JComponent,
  ): JComponent {
    val toggleDeepInspectAction =
      ToggleDeepInspectAction(
        isSelected = { rendererPanel.interceptClicks },
        setSelected = { rendererPanel.interceptClicks = it },
        isRendering = { layoutInspector.renderModel.isActive },
        connectedClientProvider = { layoutInspector.currentClient },
      )
    val shortcut =
      KeyboardShortcut(
        KeyStroke.getKeyStroke(
          KeyEvent.VK_I,
          InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK,
        ),
        null,
      )
    val shortcutSet = CustomShortcutSet(shortcut)
    toggleDeepInspectAction.registerCustomShortcutSet(shortcutSet, rootComponent)

    val processPicker =
      TargetSelectionActionFactory.getSingleDeviceProcessPicker(
        layoutInspector,
        deviceId.serialNumber,
      )
    return createEmbeddedLayoutInspectorToolbar(
      parentDisposable,
      targetComponent,
      layoutInspector,
      processPicker,
      listOf(
        toggleDeepInspectAction,
        GearAction(
          HorizontalSplitAction(::uiConfig, ::updateUi),
          SwapHorizontalSplitAction(::uiConfig, ::updateUi),
          VerticalSplitAction(::uiConfig, ::updateUi),
          SwapVerticalSplitAction(::uiConfig, ::updateUi),
          LeftVerticalSplitAction(::uiConfig, ::updateUi),
          SwapLeftVerticalSplitAction(::uiConfig, ::updateUi),
          RightVerticalSplitAction(::uiConfig, ::updateUi),
          SwapRightVerticalSplitAction(::uiConfig, ::updateUi),
          Separator.create(),
          DimensionUnitAction,
        ),
      ),
    )
  }

  /** Update the UI by rearranging the panels */
  @VisibleForTesting
  fun updateUi(uiConfig: UiConfig) {
    if (this.uiConfig == uiConfig) {
      return
    } else {
      this.uiConfig = uiConfig
      // Unwrap the UI using the old ui config.
      unwrapUi()
      // Re-wrap using the new ui config.
      wrapUi(uiConfig)
    }
  }

  override fun dispose() {
    disableLayoutInspector()
  }

  private fun disableLayoutInspector() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    unwrapUi()

    tabComponents.displayView.remove(rendererPanel)
    layoutInspector.inspectorModel.removeSelectionListener(selectionChangedListener)
    layoutInspector.processModel?.removeSelectedProcessListener(selectedProcessListener)

    tabComponents.tabContentPanelContainer.revalidate()
    tabComponents.tabContentPanelContainer.repaint()
  }

  // TODO(b/397664222) move this insider the renderer
  private val selectionChangedListener: SelectionListener = SelectionListener { _, _, _ ->
    rendererPanel.refresh()
  }

  private val selectedProcessListener = {
    // Sometimes on project close "SelectedTabContent#dispose" can be called after the listeners
    // are invoked.
    if (!project.isDisposed) {
      layoutInspector.inspectorClientSettings.inLiveMode = true
      rendererPanel.interceptClicks = false
    }
  }

  private fun createLayoutInspectorWorkbench(
    project: Project,
    parentDisposable: Disposable,
    layoutInspector: LayoutInspector,
    uiConfig: UiConfig,
    centerPanel: JComponent?,
  ): WorkBench<LayoutInspector> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val workbench = WorkBench<LayoutInspector>(project, WORKBENCH_NAME, null, parentDisposable)

    val toolsDefinition = createToolsDefinitions(uiConfig)

    if (centerPanel != null) {
      workbench.init(centerPanel, layoutInspector, toolsDefinition, false)
    } else {
      // Use a workbench] that only contains the side panels.
      workbench.init(layoutInspector, toolsDefinition, false)
    }

    return workbench
  }

  private fun createToolsDefinitions(
    uiConfig: UiConfig
  ): List<ToolWindowDefinition<LayoutInspector>> {
    return when (uiConfig) {
      UiConfig.HORIZONTAL,
      UiConfig.VERTICAL -> {
        listOf(
          createTreePanel(Side.LEFT, Split.BOTTOM),
          createPropertiesPanel(Side.RIGHT, Split.BOTTOM),
        )
      }
      UiConfig.VERTICAL_SWAP,
      UiConfig.HORIZONTAL_SWAP -> {
        listOf(
          createTreePanel(Side.RIGHT, Split.BOTTOM),
          createPropertiesPanel(Side.LEFT, Split.BOTTOM),
        )
      }
      UiConfig.LEFT_VERTICAL -> {
        listOf(
          createTreePanel(Side.LEFT, Split.TOP),
          createPropertiesPanel(Side.LEFT, Split.BOTTOM),
        )
      }
      UiConfig.LEFT_VERTICAL_SWAP -> {
        listOf(
          createTreePanel(Side.LEFT, Split.BOTTOM),
          createPropertiesPanel(Side.LEFT, Split.TOP),
        )
      }
      UiConfig.RIGHT_VERTICAL -> {
        listOf(
          createTreePanel(Side.RIGHT, Split.TOP),
          createPropertiesPanel(Side.RIGHT, Split.BOTTOM),
        )
      }
      UiConfig.RIGHT_VERTICAL_SWAP -> {
        listOf(
          createTreePanel(Side.RIGHT, Split.BOTTOM),
          createPropertiesPanel(Side.RIGHT, Split.TOP),
        )
      }
    }
  }

  private fun createTreePanel(side: Side, split: Split): LayoutInspectorTreePanelDefinition {
    return LayoutInspectorTreePanelDefinition(
      showGearAction = false,
      showHideAction = false,
      side = side,
      overrideSide = true,
      split = split,
      overrideSplit = true,
    )
  }

  private fun createPropertiesPanel(
    side: Side,
    split: Split,
  ): LayoutInspectorPropertiesPanelDefinition {
    return LayoutInspectorPropertiesPanelDefinition(
      showGearAction = false,
      showHideAction = false,
      side = side,
      overrideSide = true,
      split = split,
      overrideSplit = true,
    )
  }
}
