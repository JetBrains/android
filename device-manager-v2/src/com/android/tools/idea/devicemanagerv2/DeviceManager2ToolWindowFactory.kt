/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.isAndroidEnvironment
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

internal class DeviceManager2ToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun isApplicable(project: Project): Boolean =
    StudioFlags.UNIFIED_DEVICE_MANAGER_ENABLED.get()

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val content =
      ContentFactory.getInstance().createContent(DeviceManagerPanel(project), null, false)
    toolWindow.contentManager.addContent(content)
  }

  companion object {
    const val ID = "Device Manager 2"
  }
}
