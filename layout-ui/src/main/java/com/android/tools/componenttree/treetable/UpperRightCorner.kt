/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import javax.swing.JPanel

/**
 * A component meant to cover the area above the vertical scrollbar in the table header area.
 */
class UpperRightCorner : JPanel() {
  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintBottomSeparator(g)
  }

  private fun paintBottomSeparator(g: Graphics) {
    val g2 = g.create()
    g2.color = JBColor.border()
    g2.drawLine(0, height - 1, width, height - 1)
    g2.dispose()
  }
}
