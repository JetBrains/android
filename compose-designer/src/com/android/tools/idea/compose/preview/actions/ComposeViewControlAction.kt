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

import com.android.tools.adtui.actions.ZoomActualAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.compose.preview.isPreviewFilterEnabled
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.SwitchSurfaceLayoutManagerAction
import com.android.tools.idea.preview.actions.ViewControlAction
import com.android.tools.idea.preview.actions.isPreviewRefreshing
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform

class ComposeViewControlAction(
  layoutOptions: List<SurfaceLayoutOption>,
  isSurfaceLayoutActionEnabled: (AnActionEvent) -> Boolean = { true },
  additionalActionProvider: AnAction? = null,
) :
  ViewControlAction(
    isEnabled = { !isPreviewRefreshing(it.dataContext) },
    essentialModeDescription = message("action.scene.view.control.essentials.mode.description"),
  ) {
  init {
    if (
      StudioFlags.COMPOSE_VIEW_FILTER.get() && !PreviewEssentialsModeManager.isEssentialsModeEnabled
    ) {
      add(ComposeShowFilterAction())
      addSeparator()
    }
    add(
      SwitchSurfaceLayoutManagerAction(layoutOptions, isSurfaceLayoutActionEnabled).apply {
        isPopup = false
        templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
      }
    )
    if (StudioFlags.COMPOSE_ZOOM_CONTROLS_DROPDOWN.get()) {
      addSeparator()
      add(WrappedZoomAction(ZoomInAction.getInstance()))
      add(WrappedZoomAction(ZoomOutAction.getInstance()))
      add(WrappedZoomAction(ZoomActualAction.getInstance(), "Zoom to 100%"))
    }
    // TODO(263038548): Implement Zoom-to-selection when preview is selectable.
    addSeparator()
    add(ShowInspectionTooltipsAction())
    additionalActionProvider?.let {
      addSeparator()
      add(it)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = !isPreviewFilterEnabled(e.dataContext)
  }

  /**
   * Zoom actions have the icons, which we don't want to display in [ComposeViewControlAction]. We
   * also want to change the display text of the zoom action. (E.g. The text of [ZoomActualAction]
   * is "100%", but we'd like to display "Zoom to 100%" in the menu of [ComposeViewControlAction].
   * This class wraps a zoom action, then remove the icon and change the display text after [update]
   * is called.
   */
  private inner class WrappedZoomAction(
    private val action: AnAction,
    private val overwriteText: String? = null,
  ) : AnAction() {

    init {
      copyShortcutFrom(action)
    }

    override fun actionPerformed(e: AnActionEvent) {
      action.actionPerformed(e)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      action.update(e)
      e.presentation.icon = null
      if (overwriteText != null) {
        e.presentation.text = overwriteText
      }
    }
  }
}
