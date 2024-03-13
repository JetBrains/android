/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface.organization

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Graphics2D
import javax.swing.JComponent

/** Paints vertical lines on a left side of each organization group. */
fun Collection<Collection<JComponent>>.paintLines(g2d: Graphics2D) {
  g2d.color = OrganizationLine.COLOR
  g2d.stroke = BasicStroke(OrganizationLine.LINE_WIDTH)
  this.forEach { panels ->
    val minY = panels.filter { it.isVisible }.minOfOrNull { it.bounds.minY } ?: return@forEach
    val maxY = panels.filter { it.isVisible }.maxOfOrNull { it.bounds.maxY } ?: return@forEach
    g2d.drawLine(OrganizationLine.LINE_X, minY.toInt(), OrganizationLine.LINE_X, maxY.toInt())
  }
}

/** Properties of the line on the left side of the organization group. */
object OrganizationLine {
  const val LINE_X = 18
  const val LINE_WIDTH = 1f
  val COLOR = JBColor(JBColor.border(), UIUtil.getLabelDisabledForeground())
}
