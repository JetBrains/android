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
import org.jetbrains.annotations.TestOnly
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JPanel

/**
 * Displays a message in lieu of a button when an action may not be completed. Note, this is not an extension of JBLabel as it will display
 * other elements such as an edit link and potentially support progress indication.
 */
class StatefulButtonMessage(message: String, state: AssistActionState) : JPanel(GridBagLayout()) {
  private var myMessageDisplay: JBLabel? = null

  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    val c = GridBagConstraints()
    c.gridx = 0
    c.gridy = 0
    c.weightx = 0.01
    c.anchor = GridBagConstraints.NORTHWEST

    if (state.icon != null) {
      myMessageDisplay = JBLabel().apply {
        this.isOpaque = false
        this.icon = state.icon
        this.foreground = state.foreground
      }
      add(myMessageDisplay, c)
      c.gridx++
    }

    val section = JEditorPane()
    section.isOpaque = false
    section.border = BorderFactory.createEmptyBorder()
    section.dragEnabled = false
    UIUtils.setHtml(section, message, "body {color: " + UIUtils.getCssColor(state.foreground) + "}")
    c.fill = GridBagConstraints.HORIZONTAL
    c.weightx = 0.99
    add(section, c)
  }

  @TestOnly
  fun getMessageDisplay() = myMessageDisplay
}
