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
package com.android.tools.adtui.toolwindow.splittingtabs.actions

import com.android.tools.adtui.toolwindow.splittingtabs.isSplittingTab
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.ui.content.Content

/**
 * A base class for all Splitting Tabs related context menu (right-click) actions.
 *
 * It is responsible for enabling the action only for content that belongs to a Splitting Tabs Tool Window.
 */
internal abstract class SplittingTabsContextMenuAction(val text: String) : ToolWindowContextMenuActionBase(), DumbAware {

  init {
    templatePresentation.text = text
  }

  /**
   * Hide and disable the action for any content that is not a Splitting Tab content.
   *
   * Additionally, allow subclasses to further control the `enabled` state.
   */
  final override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    e.presentation.isEnabledAndVisible = false

    if (content?.isSplittingTab() == true) {
      e.presentation.isVisible = true
      e.presentation.isEnabled = isEnabled(content)
    }
  }

  open fun isEnabled(content: Content) = true

  /**
   * Routes actionPerformed() calls to subclass only if content is a Splitting Tabs content and required parameters are valid.
   */
  final override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    // ensure content has a manager so subclasses can !! safely
    if (content?.manager == null) {
      return
    }
    if (content.isSplittingTab()) {
      actionPerformed(content)
    }
  }

  abstract fun actionPerformed(content: Content)
}