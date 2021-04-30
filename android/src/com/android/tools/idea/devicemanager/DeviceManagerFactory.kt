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
package com.android.tools.idea.devicemanager

import com.android.tools.idea.devicemanager.groupstab.DeviceGroupsTabPanel
import com.android.tools.idea.devicemanager.physicaltab.PhysicalTabContent
import com.android.tools.idea.devicemanager.virtualtab.VirtualTab
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import icons.StudioIcons
import javax.swing.JComponent

// It should match id in a corresponding .xml
const val DEVICE_MANAGER_ID = "Device Manager"
const val virtualTabName = "Virtual"
const val deviceGroupsTabName = "Device Groups"

class DeviceManagerFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val virtualTab = VirtualTab(project)
    val contentFactory = ContentFactory.SERVICE.getInstance()

    fun createTab(content: JComponent, name: String) {
      toolWindow.contentManager.addContent(contentFactory.createContent(content, name, false))
    }

    createTab(virtualTab.content, virtualTabName)
    toolWindow.contentManager.addContent(PhysicalTabContent.create(contentFactory, project))

    if (StudioFlags.ENABLE_DEVICE_MANAGER_GROUPS.get()) {
      createTab(DeviceGroupsTabPanel(project).component, deviceGroupsTabName)
    }

    // FIXME(qumeric): create and use custom icon
    toolWindow.apply {
      setIcon(StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR)
      show(null)
      stripeTitle = "Device Manager"
    }
  }

  override fun isApplicable(project: Project): Boolean = StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get()
}
