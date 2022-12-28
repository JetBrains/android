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
package com.android.tools.idea.streaming.device.dialogs

import com.android.tools.idea.streaming.CloseDialogAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.dialog
import com.intellij.ui.components.htmlComponent
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension

/**
 * Displays a warning about privacy implications of device mirroring.
 */
internal class MirroringConfirmationDialog(val title: String) {

  private val text = "<p><b>Warning:</b> Mirroring might result in information disclosure for devices connected with" +
                     " the<code>&nbsp;adb&nbsp;tcpip </code>command because the&nbsp;mirroring information and commands are passed" +
                     " over a&nbsp;non-encrypted channel. Mirroring information might also be intercepted by other users on your host" +
                     " machine since the&nbsp;communication channel between Android Studio and the&nbsp;adb server is not encrypted.</p>"

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row {
        cell(htmlComponent(text = text, lineWrap = true)
            .apply {
              isFocusable = false
              border = JBUI.Borders.empty()
              minimumSize = Dimension(JBUIScale.scale(540), JBUIScale.scale(200))
            })
      }
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    val dialogPanel = createPanel()
    return dialog(
      title = title,
      resizable = true,
      panel = dialogPanel,
      project = project,
      parent = parent,
      createActions = {
        listOf(
          CloseDialogAction(dialogPanel, "Acknowledge", ACCEPT_EXIT_CODE, isDefault = true),
          CloseDialogAction(dialogPanel, "Disable Mirroring", REJECT_EXIT_CODE)
        )
      })
  }

  companion object {
    const val ACCEPT_EXIT_CODE = DialogWrapper.OK_EXIT_CODE
    const val REJECT_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE
  }
}
