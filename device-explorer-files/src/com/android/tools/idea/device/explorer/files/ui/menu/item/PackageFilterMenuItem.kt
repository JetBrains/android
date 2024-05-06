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
package com.android.tools.idea.device.explorer.files.ui.menu.item

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.device.explorer.files.DeviceFileEntryNode
import com.android.tools.idea.device.explorer.files.ui.DeviceFileExplorerActionListener
import com.intellij.icons.AllIcons
import javax.swing.Icon

class PackageFilterMenuItem(listener: DeviceFileExplorerActionListener) : TreeMenuItem(listener) {
  var isActionSelected = false
  var shouldBeEnabled = false

  override fun getText(nodes: List<DeviceFileEntryNode>): String {
    val selectionText = if (isActionSelected) "off" else "on"
    val buttonText = "Turn $selectionText package filter"
    return if(!shouldBeEnabled)
      "<html>$buttonText<br>Disabled due to no application IDs found</html>"
    else
      buttonText
  }

  override val icon: Icon
    get() {
      return AllIcons.General.Locate
    }

  override val shortcutId: String
    get() {
      // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      return "PackageFilter"
    }

  override val isVisible: Boolean
    get() {
      // GameTools doesn't obtain application IDs via gradle sync.
      // We might be able to make obtain application IDs more generic in the future for it to work.
      return !IdeInfo.getInstance().isGameTools
    }

  override val isEnabled: Boolean
    get() {
      return shouldBeEnabled
    }

  override fun run() {
    listener.setPackageFilter(!isActionSelected)
  }

  override fun isSelected(): Boolean {
    return isActionSelected
  }

  override fun setSelected(selected: Boolean) {}
  override fun run(nodes: List<DeviceFileEntryNode>) {}
}