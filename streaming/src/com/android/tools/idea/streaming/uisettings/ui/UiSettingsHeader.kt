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

import com.android.tools.idea.streaming.uisettings.binding.ReadOnlyProperty
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Font
import javax.swing.JComponent

private const val TITLE = "Device Settings"

internal class UiSettingsHeader(
  private val model: UiSettingsModel
) : BorderLayoutPanel() {
  init {
    background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
    add(panel {
      row(title(TITLE)) {
        this
        link(RESET_TITLE) { model.resetAction() }
          .accessibleName(RESET_TITLE)
          .apply { component.name = RESET_TITLE }
          .visibleIf(model.differentFromDefault)
          .align(AlignX.RIGHT)
      }
    }.apply {
      isOpaque = false
    })
  }

  /**
   * Create a label for the title of the panel.
   */
  private fun title(title: String): JBLabel =
    JBLabel(title).apply { font = UIUtil.getLabelFont().deriveFont(Font.BOLD) }

  private fun <T : JComponent> Cell<T>.visibleIf(predicate: ReadOnlyProperty<Boolean>): Cell<T> {
    visible(predicate.value)
    predicate.addControllerListener { visible(it) }
    return this
  }
}
