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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.common.usageInstructionsText
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class UsageInstructionsView: JPanel(BorderLayout()) {

  init {
      background = primaryContentBackground
      border = JBUI.Borders.empty(8)
      val usageInstructionsLabel = JLabel().apply {
        foreground = usageInstructionsText
        text = USAGE_INSTRUCTIONS_TEXT
      }
      add(usageInstructionsLabel)
  }

  companion object {
    const val USAGE_INSTRUCTIONS_TITLE = "<b>Usage Instructions</b>"
    private val CONTROL_KEY_TEXT = if (SystemInfo.isMac) "Command &#8984" else "Ctrl &#8963"
    private val USAGE_INSTRUCTIONS_TEXT = """
      <html>
        <h3>Navigation</h3>
        <p>You can use the mini-map at the top left of recordings to navigate to a section of the recording.</p>
        <p>Besides the mini-map, you can also use the following gestures in the threads area to navigate the recording:</p>
        <br>
        <blockquote><b>Zoom In</b>: Press <b>W</b> or scroll the mouse wheel while holding <b>$CONTROL_KEY_TEXT</b>.</blockquote>
        <blockquote><b>Zoom Out</b>: Press <b>S</b> or scroll the mouse wheel backward while holding <b>$CONTROL_KEY_TEXT</b>.</blockquote>
        <blockquote><b>Pan Left</b>: Press <b>A</b> or drag mouse right while holding <b>Space</b>.</blockquote>
        <blockquote><b>Pan Right</b>: Press <b>D</b> or drag mouse left while holding <b>Space</b>.</blockquote>
        <br>
        <h3>Analysis</h3>
        <blockquote>
        To expand or collapse a thread, double-click the thread name or press <b>Enter &#9166</b> while a thread is selected.
        </blockquote>
        <blockquote>To reorder threads, drag and drop the thread name.</blockquote>
        <blockquote>To see more details about an event, method call, or function call, select it in the timeline.</blockquote>
        <br>
      </html>
      """.trimIndent()
  }
}