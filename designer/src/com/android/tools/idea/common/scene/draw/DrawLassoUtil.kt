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
@file:JvmName("DrawLassoUtil")

package com.android.tools.idea.common.scene.draw

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D

private const val SIZE_TIP_TO_MOUSE_DISTANCE = 5
private const val SIZE_TIP_MARGIN = 6
private const val SIZE_TIP_BACKGROUND_ARC = 5
private const val SIZE_FONT = 12

private val SIZE_TIP_TEXT_COLOR = Color.WHITE
private val SIZE_TIP_TEXT_BACKGROUND = Color(0x80, 0x80, 0x80, 0xB0)

fun drawLasso(
  g: Graphics2D,
  colorSet: ColorSet,
  @SwingCoordinate x: Int,
  @SwingCoordinate y: Int,
  @SwingCoordinate width: Int,
  @SwingCoordinate height: Int,
  @SwingCoordinate mouseX: Int,
  @SwingCoordinate mouseY: Int,
  @AndroidDpCoordinate dpWidth: Int,
  @AndroidDpCoordinate dpHeight: Int,
  showSize: Boolean
) {
  val originalColor = g.color

  g.color = colorSet.lassoSelectionBorder
  g.drawRect(x, y, width, height)

  g.color = colorSet.lassoSelectionFill
  g.fillRect(x, y, width, height)

  if (showSize) {
    val originalFont = g.font
    g.font = Font(originalFont.name, Font.PLAIN, (SIZE_FONT * 0.9f).toInt())

    val sizeText = "$dpWidth x $dpHeight"
    val fm = g.fontMetrics
    val rect = fm.getStringBounds(sizeText, g)

    val sizeTipX = mouseX + SIZE_TIP_TO_MOUSE_DISTANCE
    val sizeTipY = mouseY + SIZE_TIP_TO_MOUSE_DISTANCE

    g.color = SIZE_TIP_TEXT_BACKGROUND
    g.fillRoundRect(
      sizeTipX,
      sizeTipY,
      rect.width.toInt() + SIZE_TIP_MARGIN * 2,
      rect.height.toInt() + SIZE_TIP_MARGIN * 2,
      SIZE_TIP_BACKGROUND_ARC,
      SIZE_TIP_BACKGROUND_ARC
    )

    g.color = SIZE_TIP_TEXT_COLOR
    g.drawString(sizeText, sizeTipX + SIZE_TIP_MARGIN, sizeTipY + fm.ascent + SIZE_TIP_MARGIN)

    g.font = originalFont
  }

  g.color = originalColor
}
