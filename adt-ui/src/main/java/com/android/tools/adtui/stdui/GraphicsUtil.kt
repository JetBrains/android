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

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
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

fun Rectangle2D.Float.applyInset(inset: Float) {
  this.x += inset
  this.y += inset
  this.width -= 2 * inset
  this.height -= 2 * inset
}

/**
 * Background based on [com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook] to match IJ style in toolbars.
 */
fun paintBackground(graphics: Graphics, component: JComponent) {
  val g = graphics as Graphics2D
  val size = component.size
  val config = GraphicsUtil.setupAAPainting(g)
  val opaque = UIUtil.findNearestOpaque(component)
  val bg = if (opaque != null) opaque.background else component.background

  val rect = RoundRectangle2D.Double(1.0, 1.0, (size.width - 3).toDouble(), (size.height - 3).toDouble(), 4.0, 4.0)
  if (UIUtil.isUnderAquaLookAndFeel() || SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
    val darker = ColorUtil.darker(bg, 1)
    g.color = darker
    g.fill(rect)
    g.color = Gray.xCC
    g.draw(rect)
  } else {
    val dark = UIUtil.isUnderDarcula()
    val color = if (UIUtil.isUnderWin10LookAndFeel()) Gray.xE6 else if (dark) ColorUtil.shift(bg, 1.0 / 0.7) else Gray.xD0
    g.color = color
    g.fill(rect)
    val shift = if (UIUtil.isUnderDarcula()) 1 / 0.49 else 0.49
    g.color = ColorUtil.shift(UIUtil.getPanelBackground(), shift)
    g.draw(rect)
  }
  config.restore()
}