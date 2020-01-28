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
  private val panes: MutableList<EmulatorToolWindowPane> = arrayListOf()
  private var activePane: EmulatorToolWindowPane? = null
  private var contentManagerListener = object : ContentManagerAdapter() {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        viewSelectionChanged()
      }
    }
  }

  private fun addPane(pane: EmulatorToolWindowPane, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(pane.component, pane.title, false)
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    content.tabName = pane.title
    content.icon = pane.icon
    content.popupIcon = pane.icon
    content.putUserData(ID_KEY, pane.id)

    val index = panes.binarySearch(pane, PANE_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      panes.add(index, pane)
      toolWindow.contentManager.addContent(content, index)
      if (activePane == null) {
        activePane = pane
      }
    }
  }

  private fun createContent(toolWindow: ToolWindow) {
    initialized = true
    // TODO: Discover running Emulators and create panes for each of them.
    val pane = EmulatorToolWindowPane("Pixel 3 API 29", 5554)
    addPane(pane, toolWindow)

    toolWindow.contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged()
  }

  private fun viewSelectionChanged() {
    val content = getContentManager().selectedContent
    val id = content?.getUserData<String>(ID_KEY)
    if (id != activePane?.id) {
      activePane?.destroyContent()
      activePane = null
    }
    if (id != null) {
      activePane = findPaneById(id)
      activePane?.createContent()
    }
  }

  private fun findPaneById(id: String): EmulatorToolWindowPane? {
    return panes.firstOrNull { it.id == id }
  }

  private fun getContentManager(): ContentManager {
    return ToolWindowManager.getInstance(project).getToolWindow(ID).contentManager
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    initialized = false
    toolWindow.contentManager.removeContentManagerListener(contentManagerListener)
    activePane?.destroyContent()
    activePane = null
    panes.clear()
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

private val PANE_COMPARATOR = compareBy<EmulatorToolWindowPane, Any?>(COLLATOR) { it.title }.thenBy { it.port }