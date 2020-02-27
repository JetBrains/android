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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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

/**
 * Manages contents of the Emulator tool window. Listens to changes in [RunningEmulatorCatalog]
 * and maintains [EmulatorToolWindowPanel]s, one per running Emulator.
 */
class EmulatorToolWindowManager(private val project: Project) : RunningEmulatorCatalog.Listener, DumbAware {
  private val ID_KEY = Key.create<EmulatorId>("emulator-id")

  private var initialized = false
  private val myPanels: MutableList<EmulatorToolWindowPanel> = arrayListOf()
  private var myActivePanel: EmulatorToolWindowPanel? = null
  private val emulators: MutableSet<EmulatorController> = mutableSetOf()

  private var contentManagerListener = object : ContentManagerAdapter() {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        viewSelectionChanged()
      }
    }
  }

  init {
    // Lazily initialize content since we can only have one frame.
    project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      @UiThread
      override fun stateChanged() {
        if (!SHUTDOWN_CAPABLE && initialized) {
          return
        }
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        val toolWindow = getToolWindow()
        if (toolWindow.isVisible) {
          if (!initialized) {
            initialized = true
            createContent(toolWindow)
          }
        }
        else if (SHUTDOWN_CAPABLE && initialized) {
          destroyContent(toolWindow)
        }
      }
    })
  }

  private fun createContent(toolWindow: ToolWindow) {
    initialized = true
    val emulatorCatalog = RunningEmulatorCatalog.getInstance()
    emulatorCatalog.updateNow()
    emulatorCatalog.addListener(this, EMULATOR_DISCOVERY_INTERVAL_MILLIS)
    for (emulator in emulatorCatalog.emulators) {
      addEmulatorPanel(emulator)
    }

    toolWindow.contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged()
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    initialized = false
    toolWindow.contentManager.removeContentManagerListener(contentManagerListener)
    RunningEmulatorCatalog.getInstance().removeListener(this)
    myActivePanel?.destroyContent()
    myActivePanel = null
    myPanels.clear()
  }

  private fun addEmulatorPanel(emulator: EmulatorController) {
    addEmulatorPanel(EmulatorToolWindowPanel(emulator))
  }

  private fun addEmulatorPanel(panel: EmulatorToolWindowPanel) {
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel.component, panel.title, false)
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    content.tabName = panel.title
    content.icon = panel.icon
    content.popupIcon = panel.icon
    content.putUserData(ID_KEY, panel.id)

    val index = myPanels.binarySearch(panel, PANEL_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      myPanels.add(index, panel)
      getContentManager().addContent(content, index)
      if (myActivePanel == null) {
        myActivePanel = panel
      }
    }
  }

  private fun removeEmulatorPanel(emulator: EmulatorController) {
    val panel = findPanelById(emulator.emulatorId)
    if (panel != null) {
      val contentManager = getContentManager()
      val content = contentManager.getContent(panel.component)
      contentManager.removeContent(content, true)
    }
  }

  private fun viewSelectionChanged() {
    val content = getContentManager().selectedContent
    val id = content?.getUserData(ID_KEY)
    if (id != myActivePanel?.id) {
      myActivePanel?.destroyContent()
      myActivePanel = null
    }
    if (id != null) {
      myActivePanel = findPanelById(id)
      myActivePanel?.createContent()
    }
  }

  private fun findPanelById(id: EmulatorId): EmulatorToolWindowPanel? {
    return myPanels.firstOrNull { it.id == id }
  }

  private fun getContentManager(): ContentManager {
    return getToolWindow().contentManager
  }

  private fun getToolWindow(): ToolWindow {
    return ToolWindowManager.getInstance(project).getToolWindow(ID) ?: throw IllegalStateException("Could not find Emulator tool window")
  }

  @AnyThread
  override fun emulatorAdded(emulator: EmulatorController) {
    invokeLater {
      if (initialized && emulators.add(emulator)) {
        addEmulatorPanel(emulator)
      }
    }
  }

  @AnyThread
  override fun emulatorRemoved(emulator: EmulatorController) {
    invokeLater {
      if (initialized && emulators.remove(emulator)) {
        removeEmulatorPanel(emulator)
      }
    }
  }

  private fun invokeLater(@UiThread action: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
  }

  companion object {
    const val ID = "Emulator"

    private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000
    private const val SHUTDOWN_CAPABLE = false

    @JvmStatic
    private val COLLATOR = Collator.getInstance()

    @JvmStatic
    private val PANEL_COMPARATOR = compareBy<EmulatorToolWindowPanel, Any?>(COLLATOR) { it.title }.thenBy { it.id.grpcPort }
  }
}
