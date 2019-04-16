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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.border.Border

class Separator(
  border: Border = JBUI.Borders.empty(0, 4),
  background: Color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
) : JComponent() {

  constructor(verticalInset: Int, horizontalInset: Int) :
    this(JBUI.Borders.empty(verticalInset, horizontalInset))

  private val lineWidth = JBUI.scale(1)

  init {
    this.background = background
    this.border = border
  }

  override fun paint(g: Graphics) {
    g.color = background
    val insets = insets
    g.fillRect(insets.left, insets.top, lineWidth, height - insets.top - insets.bottom)
  }

  override fun getPreferredSize(): Dimension {
    val insets = insets
    val width = lineWidth + insets.left + insets.right
    val height: Int
    if (parent != null) {
      val parentInset = parent.insets
      height = parent.height - parentInset.top - parentInset.bottom
    }
    else {
      height = super.getPreferredSize().height
    }
    return Dimension(width, height)
  }
}