/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext.Popup
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext.Toolbar
import icons.StudioIcons

class ClearAppDataMenuItem(listener: DeviceMonitorActionsListener, private val context: MenuContext) : TreeMenuItem(listener) {
  override fun getText(numOfNodes: Int): String {
    val appStr = if (numOfNodes > 1) "apps" else "app"
    return if (context == Toolbar) {
      "<html><b>Clear $appStr data</b><br>Clears application data</html>"
    } else {
      "Clear $appStr data"
    }
  }

  override val icon = StudioIcons.Common.CLEAR

  override val isVisible: Boolean
    get() {
      return when (context) {
        Popup -> listener.selectedProcessInfo.allHavePackageName()
        Toolbar -> true
      }
    }

  override val isEnabled: Boolean
    get() {
      return listener.selectedProcessInfo.allHavePackageName()
    }

  override fun run() {
    listener.clearAppData()
  }
}