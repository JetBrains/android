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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import java.text.Collator

class EmulatorToolWindow(private val project: Project) : DumbAware {
  private val ID_KEY = Key.create<String>("pane-id")

  private var initialized = false
  private val myPanels: MutableList<EmulatorToolWindowPanel> = arrayListOf()
  private var myActivePanel: EmulatorToolWindowPanel? = null
  private var contentManagerListener = object : ContentManagerAdapter() {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        viewSelectionChanged()
      }
    }
  }

  private fun addPane(panel: EmulatorToolWindowPanel, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel.component, panel.title, false)
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    content.tabName = panel.title
    content.icon = panel.icon
    content.popupIcon = panel.icon
    content.putUserData(ID_KEY, panel.id)

    val index = myPanels.binarySearch(panel, PANE_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      myPanels.add(index, panel)
      toolWindow.contentManager.addContent(content, index)
      if (myActivePanel == null) {
        myActivePanel = panel
      }
    }
  }

  private fun createContent(toolWindow: ToolWindow) {
    initialized = true
    // TODO: Discover running Emulators and create panes for each of them.
    val pane = EmulatorToolWindowPanel("Pixel 3 API 29", 5554)
    addPane(pane, toolWindow)

    toolWindow.contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged()
  }

  private fun viewSelectionChanged() {
    val content = getContentManager().selectedContent
    val id = content?.getUserData<String>(ID_KEY)
    if (id != myActivePanel?.id) {
      myActivePanel?.destroyContent()
      myActivePanel = null
    }
    if (id != null) {
      myActivePanel = findPaneById(id)
      myActivePanel?.createContent()
    }
  }

  private fun findPaneById(id: String): EmulatorToolWindowPanel? {
    return myPanels.firstOrNull { it.id == id }
  }

  private fun getContentManager(): ContentManager {
    return ToolWindowManager.getInstance(project).getToolWindow(ID).contentManager
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    initialized = false
    toolWindow.contentManager.removeContentManagerListener(contentManagerListener)
    myActivePanel?.destroyContent()
    myActivePanel = null
    myPanels.clear()
  }

  init {
    // Lazily initialize content since we can only have one frame.
    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      @UiThread
      override fun stateChanged() {
        if (!SHUTDOWN_CAPABLE && initialized) {
          return
        }
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        val window = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
        if (window.isVisible) { // TODO: How do I unsubscribe? This will keep notifying me of all tool windows, forever.
          if (!initialized) {
            initialized = true
            createContent(window)
          }
        }
        else if (SHUTDOWN_CAPABLE && initialized) {
          destroyContent(window)
        }
      }
    })
  }

  companion object {
    const val SHUTDOWN_CAPABLE = false
    const val ID = "Emulator"
  }
}

private val COLLATOR = Collator.getInstance()

private val PANE_COMPARATOR = compareBy<EmulatorToolWindowPanel, Any?>(COLLATOR) { it.title }.thenBy { it.port }