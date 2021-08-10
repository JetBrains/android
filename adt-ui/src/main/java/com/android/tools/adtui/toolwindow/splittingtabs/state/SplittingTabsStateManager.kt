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
package com.android.tools.adtui.toolwindow.splittingtabs.state

import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanel
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

@State(name = "SplittingTabsState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class SplittingTabsStateManager : PersistentStateComponent<SplittingTabsState> {
  private val toolWindows = mutableSetOf<ToolWindow>()

  private var toolWindowStates: Map<String, ToolWindowState>? = null

  override fun getState(): SplittingTabsState {
    val toolWindowStates = mutableListOf<ToolWindowState>()
    for (toolWindow in toolWindows) {
      val contentManager = toolWindow.contentManager
      val states = contentManager.contents.map {
        TabState(it.tabName, SplittingPanel.buildStateFromComponent(it.component))
      }

      val selectedContent = contentManager.selectedContent
      val index = selectedContent?.let { contentManager.getIndexOfContent(selectedContent) } ?: -1
      toolWindowStates.add(ToolWindowState(toolWindow.id, states, index))
    }
    return SplittingTabsState(toolWindowStates)
  }

  override fun loadState(state: SplittingTabsState) {
    toolWindowStates = state.toolWindows.associateBy(ToolWindowState::toolWindowId)
  }

  fun registerToolWindow(toolWindow: ToolWindow) {
    toolWindows.add(toolWindow)
  }

  fun getToolWindowState(toolWindowId: String): ToolWindowState = toolWindowStates?.get(toolWindowId) ?: ToolWindowState()

  companion object {
    fun getInstance(project: Project): SplittingTabsStateManager = project.getService(SplittingTabsStateManager::class.java)
  }
}