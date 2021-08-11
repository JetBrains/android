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
@file:Suppress("ComponentNotRegistered")

package com.android.tools.adtui.toolwindow.splittingtabs.actions

import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsBundle
import com.android.tools.adtui.toolwindow.splittingtabs.isSplittingTab
import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content

/**
 * Renames a tab.
 *
 * This action extends ToolWindowTabRenameActionBase so it cannot be a SplittingTabsContextMenuAction.
 */
internal class RenameTabAction
  : ToolWindowTabRenameActionBase("not-used", SplittingTabsBundle.message("SplittingTabsToolWindow.renameTab")), DumbAware {

  init {
    templatePresentation.text = labelText
  }

  /**
   * The base class implementation uses the ToolWindow ID but we must support arbitrary tool windows so we need a different mechanism.
   */
  override fun update(e: AnActionEvent, toolWindow: ToolWindow, selectedContent: Content?) {
    e.presentation.isEnabledAndVisible = e.project != null && selectedContent?.isSplittingTab() == true
  }
}
