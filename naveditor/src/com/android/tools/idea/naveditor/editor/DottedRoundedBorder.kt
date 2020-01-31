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
package com.android.tools.idea.naveditor.editor

import com.intellij.ui.DottedBorder
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D

class DottedRoundedBorder(insets: Insets, private val color: Color, private val cornerRadius: Float) : DottedBorder(insets, color) {
  private val dashedStroke: BasicStroke
  private val dash = floatArrayOf(3.0f)

  init {
    dashedStroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, cornerRadius, dash, 0.0f)
  }

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g as? Graphics2D ?: return
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = color
    g2.stroke = dashedStroke
    g2.draw(RoundRectangle2D.Double(x.toDouble(),
                                    y.toDouble(),
                                    width.toDouble(),
                                    height.toDouble(),
                                    cornerRadius.toDouble(),
                                    cornerRadius.toDouble())
    )
  }
}
