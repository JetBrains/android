/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers.appinspection

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.intellij.util.ui.UIUtil
import java.awt.Container
import java.awt.Cursor
import javax.swing.JPanel

// TODO(b/188695273): DELETE THIS WHOLE FILE

private val HEADER_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(26f)
private val TEXT_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(18f)

/**
 * Adds a custom rendered panel.
 *
 * The contents of the panel are displayed as follows:
 *
 *   |  [header]
 *   |  To inspect [migratingFrom], use the [migratingTo].
 *   |
 *   |  Dismiss
 *
 * The displayed [migratingTo] and Dismiss are hyperlinks that are associated
 * with [transitionAction] and [dismissAction].
 */
fun JPanel.addMigrationPanel(
  header: String,
  migratingFrom: String,
  migratingTo: String,
  transitionAction: Runnable,
  dismissAction: Runnable,
  cursorContainer: ((Container, Cursor) -> Container)? = null
): InstructionsPanel {
  val textFontMetrics = getFontMetrics(TEXT_FONT)
  val instructionsPanelBuilder = InstructionsPanel.Builder(
    TextInstruction(getFontMetrics(HEADER_FONT), header),
    NewRowInstruction(12),
    TextInstruction(textFontMetrics, "To inspect ${migratingFrom}, use the"),
    HyperlinkInstruction(TEXT_FONT, migratingTo, transitionAction),
    TextInstruction(textFontMetrics, "."),
    NewRowInstruction(24),
    HyperlinkInstruction(TEXT_FONT, "Dismiss", dismissAction))
    .setMode(InstructionsPanel.Mode.FILL_PANEL)
    .setColors(UIUtil.getInactiveTextColor(), null)

  if (cursorContainer != null) {
    instructionsPanelBuilder.setCursorSetter(cursorContainer)
  }

  return instructionsPanelBuilder.build().also { add(it) }
}