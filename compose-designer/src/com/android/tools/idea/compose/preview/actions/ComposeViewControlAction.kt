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

import com.android.tools.idea.actions.ColorBlindModeAction
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.compose.preview.isPreviewFilterEnabled
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.preview.actions.SwitchSurfaceLayoutManagerAction
import com.android.tools.idea.preview.actions.ViewControlAction
import com.android.tools.idea.preview.actions.isPreviewRefreshing
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform

class ComposeViewControlAction(
  val layoutOptions: List<SurfaceLayoutOption>,
  isSurfaceLayoutActionEnabled: (AnActionEvent) -> Boolean = { true },
  val additionalActionProvider: ColorBlindModeAction? = null,
) :
  ViewControlAction(
    isEnabled = { !isPreviewRefreshing(it.dataContext) },
    essentialModeDescription = message("action.scene.view.control.essentials.mode.description"),
  ) {
  init {
    if (ComposeShowFilterAction.shouldBeEnabled()) {
      add(ComposeShowFilterAction())
      addSeparator()
    }
    if (layoutOptions.shouldBeEnabled()) {
      add(
        SwitchSurfaceLayoutManagerAction(layoutOptions, isSurfaceLayoutActionEnabled).apply {
          isPopup = false
          templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
        }
      )
      addSeparator()
    }
    add(ShowInspectionTooltipsAction())
    additionalActionProvider?.let {
      addSeparator()
      add(it)
    }
  }

  /**
   * Action is visible if any of the following are enabled
   * * [ComposeShowFilterAction]
   * * [SwitchSurfaceLayoutManagerAction]
   * * [ShowInspectionTooltipsAction]
   * * [additionalActionProvider]
   */
  private fun hasVisibleActions(e: AnActionEvent): Boolean {
    return ComposeShowFilterAction.shouldBeEnabled() ||
      layoutOptions.shouldBeEnabled() ||
      ShowInspectionTooltipsAction.shouldBeEnabled() ||
      additionalActionProvider?.shouldBeEnabled(e) == true
  }

  /** [SwitchSurfaceLayoutManagerAction] is enabled only if there is more than one option. */
  private fun List<SurfaceLayoutOption>.shouldBeEnabled() = this.size > 1

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = !isPreviewFilterEnabled(e.dataContext) && hasVisibleActions(e)
  }
}
