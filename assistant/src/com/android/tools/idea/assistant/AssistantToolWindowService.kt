/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant

import com.android.tools.idea.assistant.AssistantToolWindowService.Companion.TOOL_WINDOW_TITLE
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import icons.StudioIcons

/**
 * Service for creating and maintaining assistant tool window content.
 *
 * Note the registration of the tool window is done programmatically (not via extension point).
 */
interface AssistantToolWindowService {
  /** Opens the assistant window and populate it with the tutorial indicated by [bundleId]. */
  fun openAssistant(bundleId: String, defaultTutorialCardId: String? = null)

  companion object {
    const val TOOL_WINDOW_TITLE = "Assistant"
  }
}

private class AssistantToolWindowServiceImpl(private val project: Project) :
  AssistantToolWindowService {

  private val assistSidePanel: AssistSidePanel by lazy { AssistSidePanel(project) }

  override fun openAssistant(bundleId: String, defaultTutorialCardId: String?) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    var toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_TITLE)

    if (toolWindow == null) {
      // NOTE: canWorkInDumbMode must be true or the window will close on gradle sync.
      toolWindow =
        toolWindowManager.registerToolWindow(
          TOOL_WINDOW_TITLE,
          false,
          ToolWindowAnchor.RIGHT,
          project,
          true
        )
      toolWindow.setIcon(StudioIcons.Shell.ToolWindows.ASSISTANT)
    }
    toolWindow.helpId = bundleId

    createAssistantContent(bundleId, toolWindow, defaultTutorialCardId)

    // Always active the window, in case it was previously minimized.
    toolWindow.activate(null)
  }

  private fun createAssistantContent(
    bundleId: String,
    toolWindow: ToolWindow,
    defaultTutorialCardId: String?
  ) {
    var content: Content? = null
    assistSidePanel.showBundle(bundleId, defaultTutorialCardId) { content?.displayName = it.name }
    val contentFactory = ContentFactory.getInstance()
    content =
      contentFactory.createContent(assistSidePanel.loadingPanel, null, false).also {
        val contentManager = toolWindow.contentManager
        contentManager.removeAllContents(true)
        contentManager.addContent(it)
        contentManager.setSelectedContent(it)
      }
    toolWindow.show(null)
  }
}
