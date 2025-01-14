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

import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.compose.preview.isPreviewFilterEnabled
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.SwitchSurfaceLayoutManagerAction
import com.android.tools.idea.preview.actions.ViewControlAction
import com.android.tools.idea.preview.actions.isPreviewRefreshing
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
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
}
