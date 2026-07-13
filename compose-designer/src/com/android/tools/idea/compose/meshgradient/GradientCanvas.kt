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

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

@Composable
fun GradientCanvas(
  modifier: Modifier = Modifier,
  meshPoints: List<List<MeshGradientPoint>>,
  showPoints: Boolean,
  constrainEdgePoints: Boolean = true,
  onTogglePoints: () -> Unit = {},
  onPointDrag: (row: Int, col: Int, offset: Offset) -> Unit,
  onPointClick: ((row: Int, col: Int) -> Unit)? = null,
) {
  Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
    BoxWithConstraints(
      modifier = Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onTogglePoints() }) }.padding(16.dp).fillMaxSize()
    ) {
      val maxWidth = constraints.maxWidth
      val maxHeight = constraints.maxHeight

      fun handlePointDrag(row: Int, col: Int, offsetX: Float, offsetY: Float) {
        val currentPoint = meshPoints[row][col]
        val currentOffset = currentPoint.position

        val x = (currentOffset.x + (offsetX / maxWidth)).coerceIn(0f, 1f)
        val y = (currentOffset.y + (offsetY / maxHeight)).coerceIn(0f, 1f)

        onPointDrag(row, col, Offset(x = x, y = y))
      }

      MeshGradient(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).fillMaxSize(),
        rows = meshPoints.size - 1,
        columns = meshPoints[0].size - 1,
        points = meshPoints,
        showPoints = showPoints,
      ) {
        Spacer(Modifier.fillMaxSize())
      }

      Layout(
        content = {
          if (showPoints) {
            val maxRow = meshPoints.size - 1
            meshPoints.forEachIndexed { rowIdx, row ->
              val maxCol = row.size - 1
              row.forEachIndexed { colIdx, col ->
                val isCorner = (rowIdx == 0 || rowIdx == maxRow) && (colIdx == 0 || colIdx == maxCol)
                val isMovable = !(constrainEdgePoints && isCorner)
                PointCursor(
                  xIndex = colIdx,
                  yIndex = rowIdx,
                  color = col.color,
                  enabled = isMovable,
                  modifier =
                    Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onPointClick?.invoke(rowIdx, colIdx) }) }
                      .pointerInput(Unit) {
                        detectDragGestures(
                          onDragStart = {
                            // Optional: handle drag start
                          },
                          onDragEnd = {
                            // Optional: handle drag end
                          },
                        ) { change, dragAmount ->
                          change.consume()
                          handlePointDrag(row = rowIdx, col = colIdx, offsetX = dragAmount.x, offsetY = dragAmount.y)
                        }
                      },
                )
              }
            }
          }
        },
        measurePolicy = { measurables, constraints ->
          val placeables = measurables.map { measurable -> measurable.measure(constraints) }

          layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeables.isNotEmpty()) {
              val cursorWidth = placeables[0].width
              val cursorHeight = placeables[0].height
              val cols = meshPoints[0].size

              placeables.forEachIndexed { i, placeable ->
                val row = i / cols
                val col = i % cols

                val xOffset = meshPoints[row][col].position.x
                val yOffset = meshPoints[row][col].position.y

                val x = ((xOffset * (constraints.maxWidth)) - cursorWidth / 2).toInt()
                val y = ((yOffset * (constraints.maxHeight)) - cursorHeight / 2).toInt()
                placeable.place(x, y)
              }
            }
          }
        },
      )
    }
  }
}
