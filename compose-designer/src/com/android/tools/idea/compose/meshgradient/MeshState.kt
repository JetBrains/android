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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.Locale

class MeshGeneratorState {
  var rows by mutableIntStateOf(3)
    private set

  var cols by mutableIntStateOf(4)
    private set

  var blurLevel by mutableFloatStateOf(0f)
  var resolution by mutableIntStateOf(10)
  var showPoints by mutableStateOf(true)
  var constrainEdgePoints by mutableStateOf(true)

  val meshPoints = mutableStateListOf<List<Pair<Offset, Color>>>()

  val generatedCode: String
    get() = generateCode()

  private val defaultColors =
    listOf(
      Color(0xFFF44336), // Red
      Color(0xFFE91E63), // Pink
      Color(0xFF9C27B0), // Purple
      Color(0xFF673AB7), // Deep Purple
      Color(0xFF3F51B5), // Indigo
      Color(0xFF2196F3), // Blue
      Color(0xFF03A9F4), // Light Blue
      Color(0xFF00BCD4), // Cyan
      Color(0xFF009688), // Teal
      Color(0xFF4CAF50), // Green
    )

  init {
    generateMeshPoints()
  }

  fun updateRows(value: Int) {
    rows = value.coerceIn(2, 10)
    generateMeshPoints()
  }

  fun updateCols(value: Int) {
    cols = value.coerceIn(2, 10)
    generateMeshPoints()
  }

  fun updateMeshPoint(row: Int, col: Int, offset: Offset) {
    if (row !in meshPoints.indices || col !in meshPoints[row].indices) return

    val colorPointsInRow = meshPoints[row].toMutableList()

    var newX = offset.x
    var newY = offset.y

    if (constrainEdgePoints) {
      newX =
        when (col) {
          0 -> 0f
          colorPointsInRow.size - 1 -> 1f
          else -> newX
        }
      newY =
        when (row) {
          0 -> 0f
          meshPoints.size - 1 -> 1f
          else -> newY
        }
    }

    val newPoint = Pair(Offset(x = newX, y = newY), colorPointsInRow[col].second)
    colorPointsInRow[col] = newPoint

    meshPoints[row] = colorPointsInRow.toList()
  }

  fun updateMeshPointColor(row: Int, col: Int, color: Color) {
    if (row !in meshPoints.indices || col !in meshPoints[row].indices) return
    val colorPointsInRow = meshPoints[row].toMutableList()
    colorPointsInRow[col] = Pair(colorPointsInRow[col].first, color)
    meshPoints[row] = colorPointsInRow.toList()
  }

  fun distributeMeshPointsEvenly() {
    val newPoints =
      meshPoints.mapIndexed { rowIdx, currentPoints ->
        val newRowPoints = mutableListOf<Pair<Offset, Color>>()
        val yPosition = if (rows > 1) rowIdx.toFloat() / (rows - 1) else 0f
        repeat(cols) { colIdx ->
          val xPosition = if (cols > 1) colIdx.toFloat() / (cols - 1) else 0f
          newRowPoints.add(Pair(Offset(xPosition, yPosition), currentPoints[colIdx].second))
        }
        newRowPoints.toList()
      }
    meshPoints.clear()
    meshPoints.addAll(newPoints)
  }

  fun updateAllPoints(transform: (Offset, Color) -> Pair<Offset, Color>) {
    val updated = meshPoints.map { row -> row.map { p -> transform(p.first, p.second) } }
    meshPoints.clear()
    meshPoints.addAll(updated)
  }

  private fun generateMeshPoints() {
    val newMeshPoints = mutableListOf<List<Pair<Offset, Color>>>()
    repeat(rows) { rowIdx ->
      val newPoints = mutableListOf<Pair<Offset, Color>>()
      val yPosition = if (rows > 1) rowIdx.toFloat() / (rows - 1) else 0f
      repeat(cols) { colIdx ->
        val xPosition = if (cols > 1) colIdx.toFloat() / (cols - 1) else 0f
        val color = defaultColors[(rowIdx * cols + colIdx) % defaultColors.size]
        newPoints.add(Pair(Offset(xPosition, yPosition), color))
      }
      newMeshPoints.add(newPoints.toList())
    }
    meshPoints.clear()
    meshPoints.addAll(newMeshPoints)
  }

  private fun generateCode(): String {
    val sb = StringBuilder()
    sb.append("val gradientPainter = remember {\n")
    sb.append(String.format(Locale.US, "    MeshGradientPainter(rows = %d, columns = %d, hasBicubicColor = true) {\n", rows - 1, cols - 1))

    meshPoints.forEachIndexed { rowIdx, row ->
      row.forEachIndexed { colIdx, (offset, color) ->
        val hexColor =
          String.format(
            "0x%02X%02X%02X%02X",
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
          )
        sb.append(
          String.format(
            Locale.US,
            "        setVertex(%d, %d, Offset(%.4ff, %.4ff), Color(%s))\n",
            rowIdx,
            colIdx,
            offset.x,
            offset.y,
            hexColor,
          )
        )
      }
    }

    sb.append("    }\n")
    sb.append("}\n\n")
    sb.append("Box(Modifier.fillMaxSize().paint(gradientPainter))\n")

    return sb.toString()
  }
}
