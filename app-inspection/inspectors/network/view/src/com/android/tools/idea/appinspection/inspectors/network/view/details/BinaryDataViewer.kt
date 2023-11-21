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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.intellij.ui.components.JBTextArea
import java.awt.FlowLayout
import java.awt.FlowLayout.LEFT
import java.awt.Font
import javax.swing.JPanel
import javax.swing.border.CompoundBorder
import javax.swing.border.MatteBorder

private val FONT = Font(Font.MONOSPACED, Font.PLAIN, 14)

/**
 * Displays a ByteArray in a traditional Hex/Ascii format.
 *
 * For example:
 * ```
 * 00000000  18 00 1c 00 f2 29 00 00  f2 29 00 00 62 42 e5 63  |.....)...)..bB.c|
 * 00000010  f2 fe 1a 06 00 00 00 00  af 27 00 00 02 46 6f 6f  |.........'...Foo|
 * ```
 */
internal class BinaryDataViewer(bytes: ByteArray) : JPanel(FlowLayout(LEFT)) {
  private val addressView = TextArea(bytes.toAddressRows())
  private val hexView = TextArea(bytes.toHexRows())
  private val asciiView =
    TextArea(bytes.toAsciiRows()).apply {
      border = CompoundBorder(MatteBorder(0, 1, 0, 1, foreground), border)
    }

  init {
    val panel = JPanel()
    panel.background = addressView.background

    add(panel)

    panel.add(addressView)
    panel.add(hexView)
    panel.add(asciiView)

    panel.maximumSize = panel.preferredSize
  }

  private class TextArea(text: String) : JBTextArea(text) {
    init {
      font = FONT
      border = MatteBorder(8, 8, 8, 8, background)
    }
  }
}

private fun ByteArray.toAddressRows(): String {
  val rows = ((size - 1) / 16) + 1
  return (0 until rows).joinToString("\n") { "%08x".format(it * 16) }
}

private fun ByteArray.toHexRows(): String {
  return asSequence()
    .chunked(16) { row ->
      row
        .chunked(8) { block -> block.joinToString(" ") { "%02x".format(it) } }
        .joinToString("  ") { it }
    }
    .joinToString("\n") { it.padEnd(16 * 3) }
}

private fun ByteArray.toAsciiRows(): String {
  return asSequence()
    .chunked(16) { row ->
      row.joinToString("") {
        val char = it.toInt().toChar()
        when {
          char.isPrintable() -> char.toString()
          else -> "."
        }
      }
    }
    .joinToString("\n") { it.padEnd(16, ' ') }
}

private fun Char.isPrintable() = !Character.isISOControl(this) && this.code < 127
