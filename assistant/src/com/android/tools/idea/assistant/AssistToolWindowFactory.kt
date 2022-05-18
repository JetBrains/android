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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.apache.http.concurrent.FutureCallback

class AssistToolWindowFactory(private val myActionId: String) : ToolWindowFactory {

  private var content: Content? = null

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    createToolWindowContent(project, toolWindow, false)
  }

  /** If usePanelTitle is true, the bundle's name will show on the top bar next to the title */
  fun createToolWindowContent(project: Project, toolWindow: ToolWindow, usePanelTitle: Boolean) {
    val assistSidePanel =
        if (usePanelTitle) AssistSidePanel(myActionId, project, PanelTitleCallback())
        else AssistSidePanel(myActionId, project, null)
    val contentFactory = ContentFactory.SERVICE.getInstance()
    content =
        contentFactory.createContent(assistSidePanel.loadingPanel, null, false).also {
          val contentManager = toolWindow.contentManager
          contentManager.removeAllContents(true)
          contentManager.addContent(it)
          contentManager.setSelectedContent(it)
          toolWindow.show(null)
        }
  }

  private inner class PanelTitleCallback : FutureCallback<String> {
    override fun completed(result: String) {
      content?.displayName = result
    }

    override fun failed(ex: Exception) {}
    override fun cancelled() {}
  }
}
