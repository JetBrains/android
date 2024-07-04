/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.isPreviewFilterEnabled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.CommonIssueNotificationAction
import com.android.tools.idea.preview.actions.ForceCompileAndRefreshActionForNotification
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.Insets

/**
 * Action that reports the current state of the Compose Preview.
 *
 * This action reports:
 * - State of Live Edit or preview out of date if Live Edit is disabled
 * - Syntax errors
 */
class PreviewIssueNotificationAction(parentDisposable: Disposable) :
  CommonIssueNotificationAction(), RightAlignedToolbarAction {

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun margins(): Insets {
    return JBUI.insets(3, 7)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (StudioFlags.COMPOSE_VIEW_FILTER.get()) {
      e.presentation.isVisible = !isPreviewFilterEnabled(e.dataContext)
    }
  }
}

private class HideIfFiltersEnabledWrapper(
  private val delegate: ForceCompileAndRefreshActionForNotification
) : AnActionWrapper(delegate), RightAlignedToolbarAction, CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String) =
    delegate.createCustomComponent(this, presentation, place)

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (StudioFlags.COMPOSE_VIEW_FILTER.get()) {
      val manager = e.getData(DESIGN_SURFACE)?.let { COMPOSE_PREVIEW_MANAGER.getData(it) } ?: return
      e.presentation.isVisible = !manager.isFilterEnabled
    }
  }
}

private fun ForceCompileAndRefreshActionForNotification.hideIfFiltersEnabled() =
  HideIfFiltersEnabledWrapper(this)

/**
 * [DefaultActionGroup] that shows the notification chip and the
 * [ForceCompileAndRefreshActionForNotification] button when applicable.
 */
class ComposeNotificationGroup(parentDisposable: Disposable) :
  DefaultActionGroup(
    listOf(
      ComposeHideFilterAction(),
      PreviewIssueNotificationAction(parentDisposable),
      ForceCompileAndRefreshActionForNotification.getInstance().hideIfFiltersEnabled(),
    )
  )
