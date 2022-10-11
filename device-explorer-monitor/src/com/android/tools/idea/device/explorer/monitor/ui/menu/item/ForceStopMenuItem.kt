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
package com.android.tools.idea.device.explorer.monitor.ui.menu.item

import com.android.tools.idea.device.monitor.ui.DeviceMonitorActionsListener
import com.android.tools.idea.device.monitor.ProcessTreeNode
import icons.StudioIcons
import javax.swing.Icon

class ForceStopMenuItem(listener: DeviceMonitorActionsListener, private val context: MenuContext) : TreeMenuItem(listener) {
  override fun getText(nodes: List<ProcessTreeNode>): String {
    return "Force stop process"
  }

  override val icon: Icon
    get() {
      return StudioIcons.AppQualityInsights.FATAL
    }

  override val shortcutId: String
    get() {
      // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      return "ForceStopProcess"
    }

  override val isVisible: Boolean
    get() {
      return if (context == MenuContext.Toolbar) true else super.isVisible
    }

  override val isEnabled: Boolean
    get() {
      return if (context == MenuContext.Toolbar) {
        listener.selectedNodes?.isNotEmpty() ?: false
      } else super.isEnabled
    }

  override fun run(nodes: List<ProcessTreeNode>) {
    listener.forceStopNodes(nodes)
  }
}