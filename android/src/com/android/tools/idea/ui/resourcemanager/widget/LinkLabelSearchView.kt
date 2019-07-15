/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.properties.Delegates

/**
 * A view that lists [LinkLabel]s centered in a [JBScrollPane].
 */
class LinkLabelSearchView: JBScrollPane() {

  fun addLabel(text: String, action: () -> Unit) {
    linkLabelsPanel.add(LinkLabel.create(text, action).apply {
      alignmentX = JPanel.CENTER_ALIGNMENT
      alignmentY = JPanel.TOP_ALIGNMENT
      border = JBUI.Borders.empty(8, 5)
      isOpaque = false
    })
    if (!isVisible) {
      isVisible = true
    }
  }

  fun clear() {
    linkLabelsPanel.removeAll()
    isVisible = false
  }

  var backgroundColor: Color by Delegates.observable(UIUtil.getListBackground()) { _, _, newValue ->
    linkLabelsPanel.background = newValue
  }

  private val linkLabelsPanel: JPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = true
    alignmentX = JPanel.CENTER_ALIGNMENT
  }

  init {
    viewport.view = linkLabelsPanel
    border = JBUI.Borders.empty()
    isVisible = false
    alignmentX = JPanel.CENTER_ALIGNMENT
    minimumSize = Dimension(JBUI.scale(10), JBUI.scale(110))
    preferredSize = Dimension(JBUI.scale(400), JBUI.scale(210))
    maximumSize = Dimension(Integer.MAX_VALUE, JBUI.scale(210))
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
  }
}