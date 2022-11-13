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
package com.android.tools.idea.streaming.emulator.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.toBinding
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Insets
import javax.swing.JTextPane
import javax.swing.border.LineBorder

/**
 * Dialog for editing snapshot parameters.
 */
class EditSnapshotDialog(snapshotName: String, snapshotDescription: String, var useToBoot: Boolean) {

  var snapshotName: String = snapshotName
    get() = field.trim()
  var snapshotDescription: String = snapshotDescription
    get() = field.trim()

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row {
        label("Name:")
      }
      row {
        textField(::snapshotName)
          .constraints(growX)
          .focused()
          .withValidationOnApply { validateSnapshotName() }
      }
      row {
        label("Description:")
      }
      row {
        component(JTextPane())
          .constraints(growX, growY, pushY)
          .withBinding(JTextPane::getText, JTextPane::setText, ::snapshotDescription.toBinding()).applyToComponent {
            background = JBTextField().background
            border = TextAreaBorder()
            preferredSize = JBUI.size(400, 64)
            text = snapshotDescription
          }
      }
      row {
        checkBox("Boot from this snapshot", ::useToBoot)
      }
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    return dialog(
      title = "Edit Snapshot",
      resizable = true,
      panel = createPanel(),
      project = project,
      parent = parent)
  }

  private fun validateSnapshotName(): ValidationInfo? {
    if (snapshotName.isEmpty()) {
      return ValidationInfo("Please enter snapshot name")
    }
    return null
  }

  class TextAreaBorder : LineBorder(JBColor.border(), 1) {

    override fun getBorderInsets(c: Component, insets: Insets): Insets {
      val left = JBUI.scale(6)
      val top = JBUI.scale(3)
      insets.set(top, left, top, left)
      return insets
    }
  }
}
