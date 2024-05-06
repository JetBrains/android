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
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.snapshots.SnapshotAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.AlphaSliderAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.LayerSpacingSliderAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RefreshAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RenderSettingsAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleLiveUpdatesAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleOverlayAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.EventQueue.invokeLater
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
 */
fun createEmbeddedLayoutInspectorToolbar(
  parentDisposable: Disposable,
  targetComponent: JComponent,
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?,
  extraActions: List<AnAction> = emptyList()
): JPanel {
  val actionToolbar =
    createStandaloneLayoutInspectorToolbar(
      parentDisposable,
      targetComponent,
      layoutInspector,
      selectProcessAction,
      extraActions
    )
  actionToolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
  actionToolbar.setReservePlaceAutoPopupIcon(false)
  actionToolbar.setOrientation(SwingConstants.HORIZONTAL)

  val toolTitleLabel = JLabel(LayoutInspectorBundle.message("layout.inspector"))
  toolTitleLabel.name = "LayoutInspectorToolbarTitleLabel"
  toolTitleLabel.border = BorderFactory.createEmptyBorder(0, 12, 0, 0)
  toolTitleLabel.font = BaseLabel.getLabelFont().deriveFont(java.awt.Font.BOLD)

  val borderLayoutPanel = BorderLayoutPanel()
  // Add the toolbar to the Border Layout to force it to always show on the far right.
  borderLayoutPanel.addToRight(actionToolbar.component)

  // Use a BoxLayout instead of a BorderLayout, because with BorderLayout if the tool window is
  // resize the label can end up overlapping the actions.
  val boxLayoutPanel = JPanel()
  boxLayoutPanel.name = EMBEDDED_LAYOUT_INSPECTOR_TOOLBAR
  boxLayoutPanel.layout = BoxLayout(boxLayoutPanel, BoxLayout.X_AXIS)
  boxLayoutPanel.add(toolTitleLabel)
  // Add some spacing between the label and toolbar.
  boxLayoutPanel.add(Box.createRigidArea(JBUI.size(10, 0)))
  boxLayoutPanel.add(borderLayoutPanel)

  return boxLayoutPanel
}

/** * Creates the toolbar used by Stadalone Layout Inspector. */
fun createStandaloneLayoutInspectorToolbar(
  parentDisposable: Disposable,
  targetComponent: JComponent,
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?,
  extraActions: List<AnAction> = emptyList()
): ActionToolbar {
  val actionGroup = LayoutInspectorActionGroup(layoutInspector, selectProcessAction, extraActions)
  val actionToolbar =
    ActionManager.getInstance()
      .createActionToolbar(LAYOUT_INSPECTOR_MAIN_TOOLBAR, actionGroup, true)
  ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
  actionToolbar.component.name = LAYOUT_INSPECTOR_MAIN_TOOLBAR
  actionToolbar.component.putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
  actionToolbar.targetComponent = targetComponent
  actionToolbar.updateActionsImmediately()
  // Removes empty space on the right side of the toolbar.
  actionToolbar.setReservePlaceAutoPopupIcon(false)

  val modificationListener =
    InspectorModel.ModificationListener { _, _, _ ->
      invokeLater { actionToolbar.updateActionsImmediately() }
    }
  layoutInspector.inspectorModel.addModificationListener(modificationListener)

  Disposer.register(parentDisposable) {
    layoutInspector.inspectorModel.removeModificationListener(modificationListener)
  }

  return actionToolbar
}

/** Action Group containing all the actions used in Layout Inspector's main toolbar. */
private class LayoutInspectorActionGroup(
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?,
  extraActions: List<AnAction>
) : DefaultActionGroup() {
  init {
    if (selectProcessAction != null) {
      add(selectProcessAction)
    }
    add(Separator.getInstance())
    add(
      RenderSettingsAction(
        renderModelProvider = { layoutInspector.renderModel },
        renderSettingsProvider = { layoutInspector.renderLogic.renderSettings }
      )
    )
    add(ToggleOverlayAction { layoutInspector.renderModel })
    if (!layoutInspector.isSnapshot) {
      add(SnapshotAction)
    }
    add(AlphaSliderAction { layoutInspector.renderModel })
    if (
      !layoutInspector.isSnapshot &&
        !LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
    ) {
      add(Separator.getInstance())
      add(ToggleLiveUpdatesAction(layoutInspector))
      add(RefreshAction)
    }
    add(Separator.getInstance())
    add(LayerSpacingSliderAction { layoutInspector.renderModel })
    extraActions.forEach { add(it) }
  }
}
