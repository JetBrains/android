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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.android.tools.adtui.toolwindow.splittingtabs.actions.NewTabAction
import com.android.tools.adtui.toolwindow.splittingtabs.state.PanelState
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateManager
import com.android.tools.adtui.toolwindow.splittingtabs.state.TabState
import com.android.tools.adtui.toolwindow.splittingtabs.state.ToolWindowState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import java.awt.event.KeyEvent
import javax.swing.JComponent

abstract class SplittingTabsToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val stateManager = SplittingTabsStateManager.getInstance(project)
    stateManager.registerToolWindow(toolWindow)

    val contentManager = toolWindow.contentManager
    val newTabAction = NewTabAction(SplittingTabsBundle.lazyMessage("SplittingTabsToolWindow.newTab")) {
      createNewTab(project, contentManager)
    }
    newTabAction.registerCustomShortcutSet(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK, toolWindow.component)
    (toolWindow as ToolWindowEx).setTabActions(newTabAction)

    val toolWindowState = stateManager.getToolWindowState(toolWindow.id)
    if (toolWindowState.tabStates.isEmpty()) {
      createNewTab(project, contentManager)
    }
    else {
      restoreTabs(project, contentManager, toolWindowState)
    }

    project.messageBus.connect().subscribe(
      ToolWindowManagerListener.TOPIC,
      object : ToolWindowManagerListener {
        override fun toolWindowShown(shownToolWindow: ToolWindow) {
          if (toolWindow === shownToolWindow && toolWindow.isVisible && contentManager.isEmpty) {
            // open a new session if all tabs were closed manually
            createNewTab(project, contentManager)
          }
        }
      })
  }

  abstract fun generateTabName(tabNames: Set<String>): String

  abstract fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?): JComponent

  private fun restoreTabs(project: Project, contentManager: ContentManager, toolwindowState: ToolWindowState) {
    toolwindowState.run {
      tabStates.forEachIndexed { index, state -> createNewTab(project, contentManager, state, index == selectedTabIndex) }
    }
  }

  private fun createNewTab(
    project: Project, contentManager: ContentManager, tabState: TabState? = null, requestFocus: Boolean = false): Content {
    val content = createContent(project, contentManager, tabState)
    contentManager.addContent(content)
    contentManager.setSelectedContent(content, requestFocus)
    return content
  }

  protected fun createNewTab(toolWindow: ToolWindowEx, tabName: String, clientState: String?): Content {
    val contentManager = toolWindow.contentManager
    return createNewTab(toolWindow.project, contentManager, TabState(tabName, PanelState(clientState)))
  }

  private fun createContent(project: Project, contentManager: ContentManager, tabState: TabState?): Content {
    val tabName = tabState?.tabName ?: generateTabName(contentManager.contents.mapTo(hashSetOf()) { it.displayName })
    return contentManager.factory.createContent(null, tabName, false).also { content ->
      content.isCloseable = true
      content.component = SplittingPanel.buildComponentFromState(content, tabState?.panelState, object : ChildComponentFactory {
        override fun createChildComponent(state: String?, popupActionGroup: ActionGroup): JComponent =
          createChildComponent(project, popupActionGroup, state)
      })
    }
  }
}