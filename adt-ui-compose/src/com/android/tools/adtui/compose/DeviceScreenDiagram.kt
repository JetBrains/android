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
package com.android.tools.adtui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle

/**
 * Displays a diagram representing a device screen of the given dimensions.
 *
 * @param width the width of the screen in pixels
 * @param height the height of the screen in pixels
 * @param diagonalLength the diagonal length of the screen as a String, e.g. 6.2"
 * @param round if the screen is circular; if true, height must be equal to width
 */
@Composable
fun DeviceScreenDiagram(
  width: Int,
  height: Int,
  modifier: Modifier = Modifier,
  diagonalLength: String = "",
  round: Boolean = false,
) {
  check(!round || height == width) { "Round screens have equal width and height" }

  val textMeasurer = rememberTextMeasurer()
  val textStyle = LocalTextStyle.current
  val widthHeightTextStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9)
  val contentColor = LocalContentColor.current
  val widthTextMeasurement =
    remember(width, textStyle) {
      textMeasurer.measure("$width px", maxLines = 1, style = widthHeightTextStyle)
    }
  val heightTextMeasurement =
    remember(height, textStyle) {
      textMeasurer.measure("$height px", maxLines = 1, style = widthHeightTextStyle)
    }
  val diagonalTextMeasurement =
    remember(diagonalLength, textStyle) {
      textMeasurer.measure(
        diagonalLength,
        style = textStyle.copy(fontSize = textStyle.fontSize * 1.2),
        maxLines = 1,
      )
    }
  val aspectRatio = width.toFloat() / height
  val screenColor =
    rememberColor(IntUiPaletteDefaults.Dark.Gray2, IntUiPaletteDefaults.Light.Gray14)
  val diagonalColor =
    rememberColor(IntUiPaletteDefaults.Dark.Gray3, IntUiPaletteDefaults.Light.Gray12)

  Canvas(
    modifier
      // Constrain the minimum size so that the draw area doesn't go negative on inset()
      .requiredSizeIn(minWidth = 50.dp, minHeight = 50.dp)
      .fillMaxSize()
  ) {
    // While there is an aspectRatio modifier, it constrains the entire diagram including labels,
    // and we want the inner screen portion to have the correct aspect ratio.
    var verticalInset = 0f
    var horizontalInset = 0f
    var verticalDiagramInset = 0f
    var horizontalDiagramInset = 0f
    val innerWidth = (size.width - heightTextMeasurement.size.height.toFloat() - 4.dp.toPx()).coerceAtLeast(0f)
    val innerHeight = (size.height - widthTextMeasurement.size.height.toFloat() - 4.dp.toPx()).coerceAtLeast(0f)
    if (innerHeight * aspectRatio > innerWidth) {
      verticalDiagramInset = ((innerHeight - (innerWidth / aspectRatio)) / 2) - 4.dp.toPx()
      verticalInset = verticalDiagramInset.coerceAtMost((size.height - heightTextMeasurement.size.width.toFloat()) / 2)
    } else {
      horizontalDiagramInset = ((innerWidth - (innerHeight * aspectRatio)) / 2) - 4.dp.toPx()
      horizontalInset = horizontalDiagramInset.coerceAtMost((size.width - widthTextMeasurement.size.width.toFloat()) / 2)
    }
    inset(horizontal = horizontalInset, vertical = verticalInset) {
      inset(left = heightTextMeasurement.size.height.toFloat(), top = 0f, bottom = 0f, right = 0f) {
        drawText(
          widthTextMeasurement,
          topLeft = Offset((size.width - widthTextMeasurement.size.width) / 2, 0f),
          color = contentColor,
        )
      }
      inset(top = widthTextMeasurement.size.height.toFloat(), left = 0f, bottom = 0f, right = 0f) {
        rotate(-90f, pivot = Offset.Zero) {
          drawText(
            heightTextMeasurement,
            topLeft = Offset(-(size.height + heightTextMeasurement.size.width) / 2, 0f),
            color = contentColor,
          )
        }
      }
    }
    inset(horizontal = horizontalDiagramInset, vertical = verticalDiagramInset) {
      inset(
        left = heightTextMeasurement.size.height.toFloat(),
        top = widthTextMeasurement.size.height.toFloat(),
        right = 0f,
        bottom = 0f,
      ) {
        inset(4.dp.toPx()) {
          if (round) {
            drawCircle(screenColor)
            drawCircle(contentColor, style = Stroke(width = 1.dp.toPx()))
          } else {
            drawRoundRect(screenColor, cornerRadius = CornerRadius(12.dp.toPx()))
            drawRoundRect(
              contentColor,
              cornerRadius = CornerRadius(12.dp.toPx()),
              style = Stroke(width = 1.dp.toPx()),
            )
          }
          if (diagonalLength.isNotEmpty()) {
            val radius = if (round) (size.width / 2) else 12.dp.toPx()
            val lineOffset = (radius * (1 - 1 / Math.sqrt(2.0))).toFloat() + 4.dp.toPx()
            val textTopLeft = center - diagonalTextMeasurement.size.center.toOffset()
            drawText(diagonalTextMeasurement, topLeft = textTopLeft, color = contentColor)
            clipRect(
              left = textTopLeft.x,
              top = textTopLeft.y,
              right = textTopLeft.x + diagonalTextMeasurement.size.width,
              bottom = textTopLeft.y + diagonalTextMeasurement.size.height,
              clipOp = ClipOp.Difference,
            ) {
              drawLine(
                color = diagonalColor,
                start = Offset(lineOffset, size.height - lineOffset),
                end = Offset(size.width - lineOffset, lineOffset),
                strokeWidth = 1.dp.toPx(),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun rememberColor(dark: Long, light: Long): Color {
  val isDark = JewelTheme.isDark
  return remember(isDark) { if (isDark) Color(dark) else Color(light) }
}
