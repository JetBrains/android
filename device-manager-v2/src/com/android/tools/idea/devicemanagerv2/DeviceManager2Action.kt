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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.idea.devicemanagerv2.DeviceManagerBundle.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import icons.StudioIcons
import org.jetbrains.android.sdk.AndroidSdkUtils

internal class DeviceManager2Action : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.isVisible = StudioFlags.UNIFIED_DEVICE_MANAGER_ENABLED.get()
    presentation.icon = StudioIcons.Shell.Toolbar.DEVICE_MANAGER
    presentation.text = message("action.text")
    presentation.description = message("action.description")
    presentation.isEnabled =
      AndroidSdkUtils.isAndroidSdkAvailable() && StudioFlags.UNIFIED_DEVICE_MANAGER_ENABLED.get()
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val deviceManager =
      ToolWindowManager.getInstance(project).getToolWindow(DeviceManager2ToolWindowFactory.ID)
    deviceManager?.show(null)
  }
}
