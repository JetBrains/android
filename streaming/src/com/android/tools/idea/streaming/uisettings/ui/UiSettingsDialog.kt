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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JRootPane

/**
 * Displays a dialog with setting shortcuts.
 */
internal class UiSettingsDialog(
  project: Project,
  model: UiSettingsModel,
  deviceType: DeviceType,
  parentDisposable: Disposable
) : DialogWrapper(project, false, IdeModalityType.MODELESS) {
  private val panel = UiSettingsPanel(model, deviceType)

  init {
    init()
    Disposer.register(parentDisposable) {
      close()
    }
  }

  override fun init() {
    super.init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = JBUI.Borders.empty()
    panel.border = PopupBorder.Factory.create(true, true)
    WindowRoundedCornersManager.configure(this)

    // Close the dialog if the dialog loses focus:
    val windowListener = object : WindowAdapter() {
      override fun windowLostFocus(event: WindowEvent) {
        close()
      }
    }
    window.addWindowFocusListener(windowListener)

    // WindowMoveListener allows the window to be moved by dragging the panel.
    val moveListener = WindowMoveListener(contentPanel)
    moveListener.installTo(panel)

    Disposer.register(disposable) {
      window.removeWindowFocusListener(windowListener)
      moveListener.uninstallFrom(panel)
    }

    pack()
  }

  private fun close() {
    close(OK_EXIT_CODE)
  }

  private fun isInsideDialog(event: MouseEvent): Boolean {
    if (!isShowing) return true
    val component = event.component ?: return false
    return UIUtil.isDescendingFrom(component, window)
  }

  override fun createCenterPanel(): JComponent = panel
  override fun createActions(): Array<Action> = emptyArray()
}
