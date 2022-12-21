/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant.view

import com.android.tools.idea.assistant.AssistActionState
import com.intellij.ui.components.JBLabel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.text.DefaultCaret
import org.jetbrains.annotations.TestOnly

/**
 * Displays a message in lieu of a button when an action may not be completed. Note, this is not an
 * extension of JBLabel as it will display other elements such as an edit link and potentially
 * support progress indication.
 */
data class StatefulButtonMessage
@JvmOverloads
constructor(val title: String, val state: AssistActionState, val body: String? = null) :
  JPanel(GridBagLayout()) {

  private var myMessageDisplay: JBLabel? = null

  @TestOnly fun getMessageDisplay() = myMessageDisplay

  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false
    val c = GridBagConstraints()
    c.gridx = 0
    c.gridy = 0
    c.weighty = 0.2
    c.weightx = 0.01
    c.anchor = GridBagConstraints.NORTHWEST
    if (state.icon != null) {
      myMessageDisplay =
        JBLabel().apply {
          this.isOpaque = false
          this.icon = state.icon
          this.foreground = state.foreground
        }
      add(myMessageDisplay, c)
      c.gridx++
    }
    val titlePane = JEditorPane()
    val caret = DefaultCaret()
    caret.updatePolicy = DefaultCaret.NEVER_UPDATE
    titlePane.caret = caret
    titlePane.isOpaque = false
    titlePane.border = BorderFactory.createEmptyBorder()
    titlePane.dragEnabled = false
    UIUtils.setHtml(titlePane, title, "body {color: " + UIUtils.getCssColor(state.foreground) + "}")
    c.fill = GridBagConstraints.HORIZONTAL
    c.weightx = 0.99
    add(titlePane, c)
    body?.let {
      val bodyPane = JEditorPane()
      bodyPane.isOpaque = false
      val caret = DefaultCaret()
      caret.updatePolicy = DefaultCaret.NEVER_UPDATE
      bodyPane.caret = caret
      bodyPane.border = BorderFactory.createEmptyBorder()
      bodyPane.dragEnabled = false
      UIUtils.setHtml(bodyPane, it, "body {color: " + UIUtils.getCssColor(state.foreground) + "}")
      c.anchor = GridBagConstraints.WEST
      c.fill = GridBagConstraints.HORIZONTAL
      c.weightx = 1.0
      c.weighty = 0.8
      c.gridwidth = 2
      c.gridx = 0
      c.gridy++
      add(bodyPane, c)
    }
  }
}
