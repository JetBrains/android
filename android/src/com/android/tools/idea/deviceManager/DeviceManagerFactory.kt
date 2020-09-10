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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import icons.StudioIcons

class DeviceManagerFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val deviceManagerToolWindow = DeviceManagerToolWindow(toolWindow)
    val contentFactory = ContentFactory.SERVICE.getInstance();
    val content = contentFactory.createContent(deviceManagerToolWindow.content, "", false);
    toolWindow.contentManager.addContent(content)

    // FIXME(qumeric): create and use custom icon
    toolWindow.apply {
      setIcon(StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR)
      show(null)
      isShowStripeButton = false
      stripeTitle = "Device Manager"
    }
  }

  override fun isApplicable(project: Project): Boolean = StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get()
}