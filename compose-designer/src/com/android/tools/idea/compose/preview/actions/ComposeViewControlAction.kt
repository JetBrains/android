/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.actions.ZoomActualAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.actions.SetColorBlindModeAction
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.actions.SwitchSurfaceLayoutManagerAction
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.assertj.core.util.VisibleForTesting

class ComposeViewControlAction(
  private val layoutManagerSwitcher: LayoutManagerSwitcher,
  private val layoutManagers: List<SurfaceLayoutManagerOption>,
  private val isSurfaceLayoutActionEnabled: (AnActionEvent) -> Boolean = { true }
) :
  DropDownAction(
    message("action.scene.view.control.title"),
    message("action.scene.view.control.description"),
    AllIcons.Debugger.RestoreLayout
  ) {

  @VisibleForTesting
  public override fun updateActions(context: DataContext): Boolean {
    removeAll()
    add(
      SwitchSurfaceLayoutManagerAction(
          layoutManagerSwitcher,
          layoutManagers,
          isSurfaceLayoutActionEnabled
        )
        .apply { isPopup = false }
    )
    addSeparator()
    add(WrappedZoomAction(ZoomInAction.getInstance(), context))
    add(WrappedZoomAction(ZoomOutAction.getInstance(), context))
    add(WrappedZoomAction(ZoomActualAction.getInstance(), context, "Zoom to 100%"))
    // TODO(263038548): Implement Zoom-to-selection when preview is selectable.
    if (StudioFlags.COMPOSE_COLORBLIND_MODE.get()) {
      (context.getData(DESIGN_SURFACE) as? NlDesignSurface)?.let { surface ->
        addSeparator()
        addAction(
          DefaultActionGroup.createPopupGroup {
            message("action.scene.mode.colorblind.dropdown.title")
          }
            .apply {
              addAction(SetColorBlindModeAction(ColorBlindMode.PROTANOPES, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.PROTANOMALY, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.DEUTERANOPES, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.DEUTERANOMALY, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.TRITANOPES, surface))
            }
        )
      }
    }
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  /**
   * Zoom actions have the icons, which we don't want to display in [ComposeViewControlAction]. We
   * also want to change the display text of the zoom action. (E.g. The text of [ZoomActualAction]
   * is "100%", but we'd like to display "Zoom to 100%" in the menu of [ComposeViewControlAction].
   * This class wraps a zoom action, then remove the icon and change the display text after [update]
   * is called.
   */
  private inner class WrappedZoomAction(
    private val action: AnAction,
    private val wrappedDataContext: DataContext,
    private val overwriteText: String? = null
  ) : AnAction() {

    init {
      copyShortcutFrom(action)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val event = e.withDataContext(wrappedDataContext)
      action.actionPerformed(event)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val event = e.withDataContext(wrappedDataContext)
      action.update(event)
      event.presentation.icon = null
      if (overwriteText != null) {
        event.presentation.text = overwriteText
      }
    }
  }
}
