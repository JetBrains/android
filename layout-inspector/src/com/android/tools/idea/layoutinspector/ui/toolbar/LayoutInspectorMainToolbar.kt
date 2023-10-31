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
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.snapshots.SnapshotAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.AlphaSliderAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.LayerSpacingSliderAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RefreshAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RenderSettingsAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleLiveUpdatesAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleOverlayAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import javax.swing.JComponent

const val LAYOUT_INSPECTOR_MAIN_TOOLBAR = "LayoutInspector.MainToolbar"

/** Creates the main toolbar used by Layout Inspector. */
fun createLayoutInspectorMainToolbar(
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
