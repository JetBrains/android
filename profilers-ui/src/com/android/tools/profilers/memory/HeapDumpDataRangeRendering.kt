/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.tools.adtui.common.clickableTextColor
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.profilers.ProfilerColors
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Draw blank box for heap dump period to indicate the lack of data,
 * as well as other visual hints that the heap dump is at an instance in time
 */
fun draw(g2d: Graphics2D, clipRect: Rectangle2D, boxColor: Color) {
  val left = clipRect.x.toInt()
  val right = (clipRect.x + clipRect.width).toInt()
  val height = clipRect.height.toInt()
  val top = clipRect.y.toInt()
  val bottom = clipRect.y.toInt() + height
  val midY = top + height / 2
  g2d.apply {
    color = boxColor
    fill(clipRect)
    color = clickableTextColor
    stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(8f, 8f), 0f)
    drawLine(left, midY, right, midY)
    stroke = BasicStroke(2f)
    drawLine(left, top, left, bottom)
    drawLine(right, top, right, bottom)
  }
}