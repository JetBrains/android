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
package com.android.tools.idea.layoutinspector.ui.toolbar

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.snapshots.SnapshotAction
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorRootPanel
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.LayerSpacingSliderAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RefreshAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RenderSettingsAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleLiveUpdatesAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.EventQueue.invokeLater
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

const val LAYOUT_INSPECTOR_MAIN_TOOLBAR = "LayoutInspector.MainToolbar"
const val EMBEDDED_LAYOUT_INSPECTOR_TOOLBAR = "EmbeddedLayoutInspector.Toolbar"

/**
 * Creates the toolbar used by Embedded Layout Inspector. This toolbar is the same as the one used
 * by the Standalone Layout Inspector, but the toolbar also contains a label with the name of the
 * tool.
 *
 * @param targetComponent used as data context provider. It is necessary because some of the actions
 *   in the toolbar get LayoutInspector from [LayoutInspectorRootPanel] data context.
 * @param showTitleLabel Whether to show the "Layout Inspector" title label.
 * @param leftAlignToolbar Aligns toolbar actions on the left, otherwise on the right.
 * @param firstGroupExtraActions Actions to be added to before the first separator.
 * @param lastGroupExtraActions Actions to be added as a new group at the end.
 */
fun createEmbeddedLayoutInspectorToolbar(
  parentDisposable: Disposable,
  targetComponent: JComponent,
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?,
  showTitleLabel: Boolean = true,
  leftAlignToolbar: Boolean = false,
  firstGroupExtraActions: List<AnAction> = emptyList(),
  lastGroupExtraActions: List<AnAction> = emptyList(),
): JPanel {
  val actionGroup =
    LayoutInspectorActionGroup(
      layoutInspector = layoutInspector,
      selectProcessAction = selectProcessAction,
      firstGroupExtraActions = firstGroupExtraActions,
      middleGroupExtraActions = emptyList(),
      lastGroupExtraActions = lastGroupExtraActions,
    )

  val actionToolbar =
    createLayoutInspectorToolbarInternal(
      parentDisposable = parentDisposable,
      targetComponent = targetComponent,
      layoutInspector = layoutInspector,
      actionGroup = actionGroup,
    )

  val borderLayoutPanel = BorderLayoutPanel()
  if (leftAlignToolbar) {
    // Add the toolbar to the Border Layout to force it to always show on the far left.
    borderLayoutPanel.addToLeft(actionToolbar.component)
  } else {
    // Add the toolbar to the Border Layout to force it to always show on the far right.
    borderLayoutPanel.addToRight(actionToolbar.component)
  }

  // Use a BoxLayout instead of a BorderLayout, because with BorderLayout if the tool window is
  // resize the label can end up overlapping the actions.
  val boxLayoutPanel = JPanel()
  boxLayoutPanel.name = EMBEDDED_LAYOUT_INSPECTOR_TOOLBAR
  boxLayoutPanel.layout = BoxLayout(boxLayoutPanel, BoxLayout.X_AXIS)

  if (showTitleLabel) {
    val toolTitleLabel = JLabel(LayoutInspectorBundle.message("layout.inspector"))
    toolTitleLabel.name = "LayoutInspectorToolbarTitleLabel"
    toolTitleLabel.border = BorderFactory.createEmptyBorder(0, 12, 0, 0)
    toolTitleLabel.font = BaseLabel.getLabelFont().deriveFont(Font.BOLD)

    boxLayoutPanel.add(toolTitleLabel)
    // Add some spacing between the label and toolbar.
    boxLayoutPanel.add(Box.createRigidArea(JBUI.size(10, 0)))
  }

  boxLayoutPanel.add(borderLayoutPanel)

  return boxLayoutPanel
}

/**
 * Creates the toolbar used by Standalone Layout Inspector.
 *
 * @param targetComponent used as data context provider. It is necessary because some of the actions
 *   in the toolbar get LayoutInspector from [LayoutInspectorRootPanel] data context.
 * @param firstGroupExtraActions Actions to be added to before the first separator.
 * @param lastGroupExtraActions Actions to be added as a new group at the end.
 */
fun createStandaloneLayoutInspectorToolbar(
  parentDisposable: Disposable,
  targetComponent: JComponent,
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?,
  firstGroupExtraActions: List<AnAction> = emptyList(),
  lastGroupExtraActions: List<AnAction> = emptyList(),
): ActionToolbar {
  val middleActions =
    if (!layoutInspector.isSnapshot) {
      listOf(ToggleLiveUpdatesAction(layoutInspector), RefreshAction)
    } else {
      emptyList()
    }

  val actionGroup =
    LayoutInspectorActionGroup(
      layoutInspector = layoutInspector,
      selectProcessAction = selectProcessAction,
      firstGroupExtraActions = firstGroupExtraActions,
      middleGroupExtraActions = middleActions,
      lastGroupExtraActions = lastGroupExtraActions,
    )

  return createLayoutInspectorToolbarInternal(
    parentDisposable = parentDisposable,
    targetComponent = targetComponent,
    layoutInspector = layoutInspector,
    actionGroup = actionGroup,
  )
}

/**
 * Private helper to create the common [ActionToolbar] and set up its listeners.
 *
 * @param targetComponent used as data context provider. It is necessary because some of the actions
 *   in the toolbar get LayoutInspector from [LayoutInspectorRootPanel] data context.
 */
private fun createLayoutInspectorToolbarInternal(
  parentDisposable: Disposable,
  targetComponent: JComponent,
  layoutInspector: LayoutInspector,
  actionGroup: ActionGroup,
): ActionToolbar {
  val actionToolbar =
    ActionManager.getInstance()
      .createActionToolbar(LAYOUT_INSPECTOR_MAIN_TOOLBAR, actionGroup, true)
  ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
  actionToolbar.component.name = LAYOUT_INSPECTOR_MAIN_TOOLBAR
  actionToolbar.component.putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
  actionToolbar.targetComponent = targetComponent

  actionToolbar.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
  // Removes empty space on the right side of the toolbar.
  actionToolbar.isReservePlaceAutoPopupIcon = false
  actionToolbar.orientation = SwingConstants.HORIZONTAL

  val modificationListener =
    InspectorModel.ModificationListener { _, _, _ ->
      invokeLater { actionToolbar.updateActionsAsync() }
    }
  layoutInspector.inspectorModel.addModificationListener(modificationListener)

  Disposer.register(parentDisposable) {
    layoutInspector.inspectorModel.removeModificationListener(modificationListener)
  }

  return actionToolbar
}

/**
 * Action Group containing all the actions used in Layout Inspector's main toolbar.
 *
 * @param firstGroupExtraActions Actions to be added to before the first separator.
 * @param middleGroupExtraActions Actions to be added between the first and last separators.
 * @param lastGroupExtraActions Actions to be added as a new group at the end.
 */
private class LayoutInspectorActionGroup(
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?,
  firstGroupExtraActions: List<AnAction>,
  middleGroupExtraActions: List<AnAction>,
  lastGroupExtraActions: List<AnAction>,
) : DefaultActionGroup() {
  init {
    if (selectProcessAction != null) {
      add(selectProcessAction)
    }

    // first group
    add(Separator.getInstance())

    val rendererSettingsAction =
      RenderSettingsAction(
        renderModelProvider = { layoutInspector.renderModel },
        renderSettingsProvider = { layoutInspector.renderLogic.renderSettings },
      )
    add(rendererSettingsAction)
    firstGroupExtraActions.forEach { add(it) }
    if (!layoutInspector.isSnapshot) {
      add(SnapshotAction)
    }

    // second group
      if (middleGroupExtraActions.isNotEmpty()) {
      add(Separator.getInstance())
      middleGroupExtraActions.forEach { add(it) }
    }

    // third group
    add(Separator.getInstance())
    add(LayerSpacingSliderAction { layoutInspector.renderModel })
    lastGroupExtraActions.forEach { add(it) }
  }
}
