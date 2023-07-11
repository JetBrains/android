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

import com.android.tools.idea.streaming.StreamingBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
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
        label(StreamingBundle.message("edit.snapshot.label.name"))
      }
      row {
        textField()
          .bindText(::snapshotName)
          .align(Align.FILL)
          .focused()
          .validationOnApply { validateSnapshotName() }
      }
      row {
        label(StreamingBundle.message("edit.snapshot.label.description"))
      }
      row {
        cell(JTextPane())
          .align(Align.FILL).align(AlignY.TOP)
          .bind(JTextPane::getText, JTextPane::setText, ::snapshotDescription.toMutableProperty()).applyToComponent {
            background = JBTextField().background
            border = TextAreaBorder()
            preferredSize = JBUI.size(400, 64)
            text = snapshotDescription
          }
      }
      row {
        checkBox(StreamingBundle.message("edit.snapshot.checkbox.boot.from.this.snapshot")).bindSelected(::useToBoot)
      }
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    return dialog(
      title = StreamingBundle.message("edit.snapshot.dialog.title"),
      resizable = true,
      panel = createPanel(),
      project = project,
      parent = parent)
  }

  private fun validateSnapshotName(): ValidationInfo? {
    if (snapshotName.isEmpty()) {
      return ValidationInfo(StreamingBundle.message("edit.snapshot.enter.name"))
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
