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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorActionsListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import javax.swing.Icon

class PackageFilterMenuItem(listener: DeviceMonitorActionsListener): TreeMenuItem(listener) {
  var isActionSelected = false
  var shouldBeEnabled = false

  override fun getText(numOfNodes: Int): String {
    val selectionText = if (isActionSelected) "off" else "on"
    val buttonText = "Turn $selectionText package filter"
    return if(!shouldBeEnabled)
      "<html>$buttonText<br>Disabled due to no application IDs found</html>"
    else
      buttonText
  }

  override val action: AnAction = object : ToggleAction() {
    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.text = text
      presentation.isEnabled = isEnabled
      presentation.isVisible = isVisible
      presentation.icon = icon
      Toggleable.setSelected(presentation, isSelected(e))
    }

    override fun actionPerformed(e: AnActionEvent) {
      run()
      setSelected(e, !isSelected(e))
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return isActionSelected
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {}
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
    listener.packageFilterToggled(!isActionSelected)
  }
}