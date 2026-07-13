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
package com.android.tools.idea.compose.meshgradient

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun MeshGradient(
  modifier: Modifier = Modifier,
  rows: Int,
  columns: Int,
  hasBicubicColor: Boolean = false,
  points: List<List<MeshGradientPoint>>,
  showPoints: Boolean = false,
  content: @Composable () -> Unit = {},
): Unit {
  val pointSize = with(LocalDensity.current) { 1.5.dp.toPx() }
  val pointsPaint = remember {
    Paint().apply {
      color = Color.White.copy(alpha = 0.4f)
      strokeWidth = pointSize
      strokeCap = StrokeCap.Round
      blendMode = BlendMode.SrcOver
    }
  }
  val gradientPainter =
    remember(rows, columns, hasBicubicColor, points) {
      MeshGradientPainter(rows, columns, hasBicubicColor) {
        for (r in 0..rows) {
          for (c in 0..columns) {
            if (r < points.size && c < points[r].size) {
              setVertex(
                r,
                c,
                points[r][c].position,
                points[r][c].color,
                points[r][c].leftBezierOffset,
                points[r][c].topBezierOffset,
                points[r][c].rightBezierOffset,
                points[r][c].bottomBezierOffset,
              )
            }
          }
        }
      }
    }

  Box(
    modifier =
      modifier.paint(gradientPainter).drawBehind {
        if (showPoints) {
          val intermediatePoints = points.flatten().map { point -> Offset(point.position.x * size.width, point.position.y * size.height) }
          drawIntoCanvas { canvas -> canvas.drawPoints(pointMode = PointMode.Points, points = intermediatePoints, paint = pointsPaint) }
        }
      }
  ) {
    content()
  }
}

data class MeshGradientPoint(
  val position: Offset,
  val color: Color,
  val leftBezierOffset: Offset = Offset.Unspecified,
  val topBezierOffset: Offset = Offset.Unspecified,
  val rightBezierOffset: Offset = Offset.Unspecified,
  val bottomBezierOffset: Offset = Offset.Unspecified,
)
