/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToolWindowWindowAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi

/**
 * [ToolWindowFactory] implementation for the Emulator tool window.
 */
class StreamingToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setTitleActions(listOf(MoveToWindowAction(toolWindow)))
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED)
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.ALLOW_DND_FOR_TABS, true)
    toolWindow.component.putClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER, true)
    StreamingToolWindowManager(toolWindow as ToolWindowEx)
  }

  private class MoveToWindowAction(private val toolWindow: ToolWindow) : ToolWindowWindowAction() {
    override fun update(event: AnActionEvent) {
      when (toolWindow.type) {
        ToolWindowType.FLOATING, ToolWindowType.WINDOWED -> event.presentation.isEnabledAndVisible = false
        else -> {
          super.update(event)
          event.presentation.icon = AllIcons.Actions.MoveToWindow
        }
      }
    }
  }
}