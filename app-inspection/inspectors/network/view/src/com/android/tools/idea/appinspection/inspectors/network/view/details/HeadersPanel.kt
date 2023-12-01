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

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Font.MONOSPACED
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.StyleContext.DEFAULT_STYLE
import javax.swing.text.StyledDocument

/** Displays headers from a connection */
internal class HeadersPanel(map: Map<String, List<String>>) :
  JBScrollPane(VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
  init {
    val textPane = JTextPane()
    val document = textPane.styledDocument
    val style =
      StyleContext.getDefaultStyleContext().getStyle(DEFAULT_STYLE).apply {
        StyleConstants.setFontFamily(this, MONOSPACED)
        StyleConstants.setFontSize(this, 14)
      }
    val regularStyle = document.addStyle("regular", style)
    val boldStyle = document.addStyle("bold", style).apply { StyleConstants.setBold(this, true) }

    val width = map.keys.maxOf { it.length } + 5
    map.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (key, value) ->
      document.append("%-${width}s".format("$key:"), boldStyle)
      document.append("${value.first()}\n", regularStyle)
      value.drop(1).forEach {
        document.append(" ".repeat(width), regularStyle)
        document.append("$it\n", regularStyle)
      }
    }
    val noWrapPanel = JPanel(BorderLayout())
    noWrapPanel.add(textPane)
    setViewportView(noWrapPanel)
  }
}

private fun StyledDocument.append(text: String, style: Style) {
  insertString(length, text, style)
}
