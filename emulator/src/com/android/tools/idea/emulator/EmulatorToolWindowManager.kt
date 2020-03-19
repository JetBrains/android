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
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
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
internal class EmulatorToolWindowManager(private val project: Project) : RunningEmulatorCatalog.Listener, DumbAware {
  private val ID_KEY = Key.create<EmulatorId>("emulator-id")

  private var initialized = false
  private val panels: MutableList<EmulatorToolWindowPanel> = arrayListOf()
  private var activePanel: EmulatorToolWindowPanel? = null
  private val emulators: MutableSet<EmulatorController> = hashSetOf()
  private val properties = PropertiesComponent.getInstance(project)

  private var contentManagerListener = object : ContentManagerAdapter() {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        viewSelectionChanged()
      }
    }
  }

  private val frameIsCropped
    get() = properties.getBoolean(FRAME_CROPPED_PROPERTY, FRAME_CROPPED_DEFAULT)

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

    val actionGroup = DefaultActionGroup()
    actionGroup.addAction(ToggleFrameCropAction())
    (toolWindow as ToolWindowEx).setAdditionalGearActions(actionGroup)

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
    activePanel?.destroyContent()
    activePanel = null
    panels.clear()
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

    val index = panels.binarySearch(panel, PANEL_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      panels.add(index, panel)
      getContentManager().addContent(content, index)
      if (activePanel == null) {
        activePanel = panel
      }
    }
  }

  private fun removeEmulatorPanel(emulator: EmulatorController) {
    val panel = findPanelByGrpcPort(emulator.emulatorId.grpcPort)
    if (panel != null) {
      panels.remove(panel)
      val contentManager = getContentManager()
      val content = contentManager.getContent(panel.component)
      contentManager.removeContent(content, true)
    }
  }

  private fun viewSelectionChanged() {
    val content = getContentManager().selectedContent
    val id = content?.getUserData(ID_KEY)
    if (id != activePanel?.id) {
      activePanel?.destroyContent()
      activePanel = null

      if (id != null) {
        activePanel = findPanelByGrpcPort(id.grpcPort)
        activePanel?.createContent(frameIsCropped)
      }
    }
  }

  private fun findPanelByGrpcPort(grpcPort: Int): EmulatorToolWindowPanel? {
    return panels.firstOrNull { it.id.grpcPort == grpcPort }
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

  private inner class ToggleFrameCropAction : ToggleAction("Crop Device Frame"), DumbAware {
    override fun isSelected(event: AnActionEvent): Boolean {
      return frameIsCropped
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      properties.setValue(FRAME_CROPPED_PROPERTY, state, FRAME_CROPPED_DEFAULT)
      for (panel in panels) {
        panel.emulatorView?.cropFrame = state
      }
    }
  }

  companion object {
    const val ID = "Emulator"

    private const val FRAME_CROPPED_PROPERTY = "com.android.tools.idea.emulator.frame.cropped"
    private const val FRAME_CROPPED_DEFAULT = true
    private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000
    private const val SHUTDOWN_CAPABLE = false // TODO: Change to true.

    @JvmStatic
    private val COLLATOR = Collator.getInstance()

    @JvmStatic
    private val PANEL_COMPARATOR = compareBy<EmulatorToolWindowPanel, Any?>(COLLATOR) { it.title }.thenBy { it.id.grpcPort }
  }
}
