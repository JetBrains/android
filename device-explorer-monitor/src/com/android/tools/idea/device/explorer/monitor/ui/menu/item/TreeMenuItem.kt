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

import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorActionsListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.Icon

/**
 * A base popup menu item.
 */
abstract class TreeMenuItem(protected val listener: DeviceMonitorActionsListener) : PopupMenuItem {
  override val text: String
    get() {
      return getText(listener.numOfSelectedNodes)
    }

  override val icon: Icon?
    get() {
      return null
    }

  override val action: AnAction = object : AnAction(icon) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.text = text
      presentation.isEnabled = isEnabled
      presentation.isVisible = isVisible
      presentation.icon = icon
    }

    override fun actionPerformed(e: AnActionEvent) {
      run()
    }
  }
  abstract fun getText(numOfNodes: Int): String
}