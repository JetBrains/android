/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor.ui.menu.item

import com.android.ddmlib.ClientData
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.device.explorer.monitor.processes.isPidOnly
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorActionsListener
import com.android.tools.idea.execution.common.debug.RunConfigurationWithDebugger
import com.intellij.execution.RunManager
import com.intellij.icons.AllIcons
import javax.swing.Icon

class DebugMenuItem(
  listener: DeviceMonitorActionsListener,
  private val context: MenuContext,
  private val runManager: RunManager) : TreeMenuItem(listener) {
  override fun getText(numOfNodes: Int): String {
    val config = runManager.selectedConfiguration?.configuration as? RunConfigurationWithDebugger
    // Prioritize number of selected processes first.
    return if (numOfNodes == 0) {
      "Attach debugger"
    } else if (config?.androidDebuggerContext?.androidDebugger == null) {
      "Selected run configuration doesn't support debuggers"
    } else {
      "Attach debugger"
    }
  }

  override val icon: Icon
    get() {
      return AllIcons.Debugger.AttachToProcess
    }

  override val shortcutId: String
    get() {
      // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      return "AttachDebugger"
    }

  override val isVisible: Boolean
    get() {
      return if (!IdeInfo.getInstance().isGameTools) {
        if (context == MenuContext.Popup) {
          listener.selectedProcessInfo.any { !it.isPidOnly }
        } else true
      } else {
        // We currently don't have a communication mechanism from Game Tools process back into Visual Studio
        false
      }
    }

  override val isEnabled: Boolean
    get() {
      val selectedInfoList = listener.selectedProcessInfo
      if (selectedInfoList.isEmpty()) {
        return false
      }

      val config = runManager.selectedConfiguration?.configuration as? RunConfigurationWithDebugger
      config?.androidDebuggerContext?.androidDebugger ?: return false

      for (info in selectedInfoList) {
        if (info.debuggerStatus == ClientData.DebuggerStatus.DEFAULT) {
          return true
        }
      }

      return false
    }

  override fun run() {
    listener.debugNodes()
  }
}