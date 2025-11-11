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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.SPLITTER_KEY
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.stateinspection.createStateInspectionPanel
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorRootPanel
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.INITIAL_ALPHA_VALUE
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.OverlayActionGroup
import com.android.tools.idea.layoutinspector.ui.toolbar.createEmbeddedLayoutInspectorToolbar
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val WORKBENCH_NAME = "Layout Inspector"
const val STATE_READ_SPLITTER_NAME = "StateReadSplitter"

/** Contains the state of Layout Inspector toolbar actions. */
class ToolbarState(val showTitle: Boolean = true, val leftAlightToolbar: Boolean = false) {
  private val _isDeepInspectEnabled = MutableStateFlow(false)
  val isDeepInspectEnabled = _isDeepInspectEnabled.asStateFlow()

  private val _overlayImage = MutableStateFlow<ByteArray?>(null)
  val overlayImage = _overlayImage.asStateFlow()

  private val _overlayTransparency = MutableStateFlow(INITIAL_ALPHA_VALUE)
  val overlayTransparency = _overlayTransparency.asStateFlow()

  fun setDeepInspectEnabled(enabled: Boolean) {
    _isDeepInspectEnabled.value = enabled
  }

  fun setOverlayImage(image: ByteArray?) {
    _overlayImage.value = image
  }

  fun setOverlayTransparency(transparency: Float) {
    _overlayTransparency.value = transparency
  }
}

/**
 * Creates the main Layout Inspector panel.
 *
 * @param uiConfig defines the configuration of the panel - where to place the main and side panels.
 * @param centerPanel optional center panel rendered in the workbench. When null the workbench only
 *   has the side panels.
 * @param processPicker optional process/device picker.
 * @param extraToolbarActions list of extra actions to add to the toolbar.
 * @param toolbarState contains the state of some toolbar actions.
 */
fun createLayoutInspectorPanel(
  project: Project,
  disposable: Disposable,
  layoutInspector: LayoutInspector,
  uiConfig: UiConfig,
  centerPanel: JComponent?,
  processPicker: AnAction?,
  extraToolbarActions: List<AnAction> = emptyList(),
  toolbarState: ToolbarState = ToolbarState(),
): LayoutInspectorRootPanel {
  val inspectorPanel = BorderLayoutPanel()

  val toolbarPanel =
    createToolbarPanel(
      disposable = disposable,
      layoutInspector = layoutInspector,
      rootComponent = inspectorPanel,
      processPicker = processPicker,
      extraActions = extraToolbarActions,
      toolbarState = toolbarState,
    )

  val mainPanel =
    when (uiConfig) {
      UiConfig.HORIZONTAL,
      UiConfig.HORIZONTAL_SWAP -> {
        // Create a vertical split panel containing the device view at the top and the tool
        // windows at the bottom.
        val toolsPanel =
          createToolsPanel(
            project = project,
            disposable = disposable,
            layoutInspector = layoutInspector,
            uiConfig = uiConfig,
            centerPanel = null,
            toolbarPanel = toolbarPanel,
          )
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
        createToolsPanel(
          project = project,
          disposable = disposable,
          layoutInspector = layoutInspector,
          uiConfig = uiConfig,
          centerPanel = centerPanel,
          toolbarPanel = toolbarPanel,
        )
      }
    }

  val inspectorBanner = InspectorBanner(disposable, layoutInspector.notificationModel)
  inspectorPanel.add(inspectorBanner, BorderLayout.NORTH)
  inspectorPanel.add(mainPanel, BorderLayout.CENTER)
  return LayoutInspectorRootPanel(inspectorPanel, layoutInspector)
}

/**
 * Creates the Layout Inspector panel containing toolbar and the workbench.
 *
 * @param uiConfig defines the configuration of the panel - where to place the main and side panels.
 * @param centerPanel optional center panel rendered in the workbench. When null the workbench only
 *   has the side panels.
 * @param toolbarPanel the panel containing the toolbar.
 */
private fun createToolsPanel(
  project: Project,
  disposable: Disposable,
  layoutInspector: LayoutInspector,
  uiConfig: UiConfig,
  centerPanel: JComponent?,
  toolbarPanel: JPanel,
): JPanel {
  val workBench =
    createLayoutInspectorWorkbench(project, disposable, layoutInspector, uiConfig, centerPanel)
  workBench.isFocusCycleRoot = false
  workBench.component.border = JBUI.Borders.customLineTop(JBColor.border())

  // Split panel used for inspection of State Reads in Compose.
  val splitPanel =
    OnePixelSplitter(true, SPLITTER_KEY, 0.65f).apply {
      name = STATE_READ_SPLITTER_NAME
      firstComponent = workBench
      secondComponent = createStateInspectionPanel(layoutInspector, disposable)
      setBlindZone { JBUI.insets(0, 1) }
    }

  return BorderLayoutPanel().apply {
    add(toolbarPanel, BorderLayout.NORTH)
    add(splitPanel, BorderLayout.CENTER)
  }
}

/**
 * Creates a Layout Inspector toolbar.
 *
 * @param rootComponent used to register keyboard shortcuts and as data-context retrieval.
 * @param processPicker optional process/device picker.
 * @param extraActions list of extra actions to add to the toolbar.
 * @param toolbarState contains the state of some toolbar actions.
 */
private fun createToolbarPanel(
  disposable: Disposable,
  layoutInspector: LayoutInspector,
  rootComponent: JComponent,
  processPicker: AnAction?,
  extraActions: List<AnAction> = emptyList(),
  toolbarState: ToolbarState = ToolbarState(),
): JPanel {
  val toggleDeepInspectAction =
    ToggleDeepInspectAction(
      isSelected = { toolbarState.isDeepInspectEnabled.value },
      setSelected = { toolbarState.setDeepInspectEnabled(it) },
      isRendering = { layoutInspector.renderModel.isActive },
      connectedClientProvider = { layoutInspector.currentClient },
    )
  // TODO(b/449698912): shortcut does not work
  val toggleDeepInspectShortcut =
    KeyboardShortcut(
      KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK),
      null,
    )
  val shortcutSet = CustomShortcutSet(toggleDeepInspectShortcut)
  toggleDeepInspectAction.registerCustomShortcutSet(shortcutSet, rootComponent)

  val overlayActionGroup =
    OverlayActionGroup(
      inspectorModel = layoutInspector.inspectorModel,
      getImage = { toolbarState.overlayImage.value },
      setImage = { toolbarState.setOverlayImage(it) },
      setAlpha = { toolbarState.setOverlayTransparency(it) },
    )

  return createEmbeddedLayoutInspectorToolbar(
    parentDisposable = disposable,
    targetComponent = rootComponent,
    layoutInspector = layoutInspector,
    selectProcessAction = processPicker,
    showTitleLabel = toolbarState.showTitle,
    leftAlignToolbar = toolbarState.leftAlightToolbar,
    firstGroupExtraActions = listOf(overlayActionGroup),
    lastGroupExtraActions = listOf(toggleDeepInspectAction) + extraActions,
  )
}

private fun createLayoutInspectorWorkbench(
  project: Project,
  disposable: Disposable,
  layoutInspector: LayoutInspector,
  uiConfig: UiConfig,
  centerPanel: JComponent?,
): WorkBench<LayoutInspector> {
  ApplicationManager.getApplication().assertIsDispatchThread()
  val workbench = WorkBench<LayoutInspector>(project, WORKBENCH_NAME, null, disposable)

  val toolsDefinition = createToolsDefinitions(uiConfig)

  if (centerPanel != null) {
    workbench.init(centerPanel, layoutInspector, toolsDefinition, false)
  } else {
    // Use a workbench] that only contains the side panels.
    workbench.init(layoutInspector, toolsDefinition, false)
  }

  return workbench
}

/** Creates the [ToolWindowDefinition] to use as side panels in Layout Inspector workbench. */
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
      listOf(createTreePanel(Side.LEFT, Split.TOP), createPropertiesPanel(Side.LEFT, Split.BOTTOM))
    }
    UiConfig.LEFT_VERTICAL_SWAP -> {
      listOf(createTreePanel(Side.LEFT, Split.BOTTOM), createPropertiesPanel(Side.LEFT, Split.TOP))
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
