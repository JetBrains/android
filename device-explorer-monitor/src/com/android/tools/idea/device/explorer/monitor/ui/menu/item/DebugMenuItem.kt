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

import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorActionsListener
import com.intellij.icons.AllIcons
import javax.swing.Icon

class DebugMenuItem(listener: DeviceMonitorActionsListener, private val context: MenuContext) : TreeMenuItem(listener) {
  override fun getText(numOfNodes: Int): String {
    return "Attach debugger"
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
      return if (context == MenuContext.Popup) listener.numOfSelectedNodes > 0 else true
    }

  override val isEnabled: Boolean
    get() {
      return listener.numOfSelectedNodes > 0
    }

  override fun run() {
    listener.debugNodes()
  }
}