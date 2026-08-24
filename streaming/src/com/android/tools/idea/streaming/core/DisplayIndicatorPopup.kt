/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

/** A pill-shaped indicator displayed over a device display view. */
class DisplayIndicatorPopup(text: String) : JPanel() {

  init {
    isOpaque = false
    border = JBUI.Borders.empty(4, 10)
    layout = BorderLayout()
    val label = JBLabel(text)
    label.foreground = JBColor.foreground()
    add(label, BorderLayout.CENTER)
  }

  override fun paint(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f)
      super.paint(g2)
    } finally {
      g2.dispose()
    }
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.color = HintUtil.getInformationColor()
      val arc = height.toDouble()
      g2.fill(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), arc, arc))
    } finally {
      g2.dispose()
    }
    super.paintComponent(g)
  }
}
