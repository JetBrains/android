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
package com.android.tools.adtui.stdui

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * Set the color and the alpha channel from the specified color value.
 */
fun Graphics2D.setColorAndAlpha(color: Color) {
  this.color = color
  if (color.alpha == 255) {
    this.composite = AlphaComposite.SrcOver
  }
  else {
    this.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.alpha / 255.0f)
  }
}

/**
 * Background based on [com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook] to match IJ style in toolbars.
 */
fun paintBackground(graphics: Graphics, component: JComponent) {
  val g = graphics as Graphics2D
  val size = component.size
  val config = GraphicsUtil.setupAAPainting(g)

  val rect = RoundRectangle2D.Double(1.0, 1.0, (size.width - 3).toDouble(), (size.height - 3).toDouble(), 4.0, 4.0)
  g.color = JBColor(Gray.xE8, Color(0x464a4d))
  g.fill(rect)
  g.color = JBColor(Gray.xCC, Color(0x757b80))
  g.draw(rect)
  config.restore()
}