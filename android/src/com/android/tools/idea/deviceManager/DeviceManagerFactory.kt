/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager

import com.android.tools.idea.deviceManager.groups.DeviceGroupsTabPanel
import com.android.tools.idea.deviceManager.physicaltab.PhysicalTabContent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.resourcemanager.RESOURCE_EXPLORER_TOOL_WINDOW_ID
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import icons.StudioIcons
import javax.swing.JComponent

// It should match id in a corresponding .xml
const val DEVICE_MANAGER_ID = "Device Manager"
const val emulatorTabName = "Emulator"
const val deviceGroupsTabName = "Device Groups"

class DeviceManagerFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val emulatorTab = EmulatorTab(project, toolWindow)
    val contentFactory = ContentFactory.SERVICE.getInstance()

    fun createTab(content: JComponent, name: String) {
      toolWindow.contentManager.addContent(contentFactory.createContent(content, name, false))
    }

    createTab(emulatorTab.content, emulatorTabName)
    toolWindow.contentManager.addContent(PhysicalTabContent(contentFactory, project).content)

    if (StudioFlags.ENABLE_DEVICE_MANAGER_GROUPS.get()) {
      createTab(DeviceGroupsTabPanel(project).component, deviceGroupsTabName)
    }

    // FIXME(qumeric): create and use custom icon
    toolWindow.apply {
      setIcon(StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR)
      show(null)
      isShowStripeButton = false
      stripeTitle = "Device Manager"
    }

    project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, MyToolWindowManagerListener(project))
  }

  override fun isApplicable(project: Project): Boolean = StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get()
}

private class MyToolWindowManagerListener(private val project: Project) : ToolWindowManagerListener {

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val window: ToolWindow = toolWindowManager.getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID) ?: return
    val contentManager = window.contentManager
    val dialogPanels = contentManager.contents.filter { it.component is DialogPanel }
    if (!window.isVisible) {
      contentManager.removeAllContents(true)
      ResourceManagerTracking.logPanelCloses()
    } else {
      dialogPanels.forEach {
        // TODO(qumeric):
      }
    }
  }
}
