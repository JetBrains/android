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

  var showPoints by mutableStateOf(true)
  var constrainEdgePoints by mutableStateOf(true)

  val meshPoints = mutableStateListOf<List<MeshGradientPoint>>()

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

  val availableColors = mutableStateListOf<Color>()

  init {
    availableColors.addAll(defaultColors)
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

  fun loadMesh(newRows: Int, newCols: Int, newPoints: List<List<MeshGradientPoint>>) {
    rows = newRows.coerceIn(2, 10)
    cols = newCols.coerceIn(2, 10)
    meshPoints.clear()
    meshPoints.addAll(newPoints)

    val loadedColors = newPoints.flatten().map { it.color }.distinct()
    loadedColors.forEach { color ->
      if (color !in availableColors) {
        availableColors.add(color)
      }
    }
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

    val newPoint = colorPointsInRow[col].copy(position = Offset(x = newX, y = newY))
    colorPointsInRow[col] = newPoint

    meshPoints[row] = colorPointsInRow.toList()
  }

  fun updateVertexColor(row: Int, col: Int, color: Color) {
    if (row !in meshPoints.indices || col !in meshPoints[row].indices) return
    val colorPointsInRow = meshPoints[row].toMutableList()
    val newPoint = colorPointsInRow[col].copy(color = color)
    colorPointsInRow[col] = newPoint
    meshPoints[row] = colorPointsInRow.toList()
  }

  fun distributeMeshPointsEvenly() {
    val newPoints =
      meshPoints.mapIndexed { rowIdx, currentPoints ->
        val newRowPoints = mutableListOf<MeshGradientPoint>()
        val yPosition = if (rows > 1) rowIdx.toFloat() / (rows - 1) else 0f
        repeat(cols) { colIdx ->
          val xPosition = if (cols > 1) colIdx.toFloat() / (cols - 1) else 0f
          newRowPoints.add(currentPoints[colIdx].copy(position = Offset(xPosition, yPosition)))
        }
        newRowPoints.toList()
      }
    meshPoints.clear()
    meshPoints.addAll(newPoints)
  }

  fun updateAllPoints(transform: (Offset, Color) -> MeshGradientPoint) {
    val updated = meshPoints.map { row -> row.map { p -> transform(p.position, p.color) } }
    meshPoints.clear()
    meshPoints.addAll(updated)
  }

  fun updatePaletteAndMeshColor(oldColor: Color, newColor: Color) {
    val index = availableColors.indexOf(oldColor)
    if (index != -1) {
      availableColors[index] = newColor
    }
    updateAllPoints { offset, currentColor ->
      if (currentColor == oldColor) {
        MeshGradientPoint(position = offset, color = newColor)
      } else {
        MeshGradientPoint(position = offset, color = currentColor)
      }
    }
  }

  private fun generateMeshPoints() {
    val newMeshPoints = mutableListOf<List<MeshGradientPoint>>()
    repeat(rows) { rowIdx ->
      val newPoints = mutableListOf<MeshGradientPoint>()
      val yPosition = if (rows > 1) rowIdx.toFloat() / (rows - 1) else 0f
      repeat(cols) { colIdx ->
        val xPosition = if (cols > 1) colIdx.toFloat() / (cols - 1) else 0f
        val color = availableColors[(rowIdx * cols + colIdx) % availableColors.size]
        newPoints.add(MeshGradientPoint(position = Offset(xPosition, yPosition), color = color))
      }
      newMeshPoints.add(newPoints.toList())
    }
    meshPoints.clear()
    meshPoints.addAll(newMeshPoints)
  }

  private fun generateCode(): String {
    val sb = StringBuilder()
    sb.append("val gradientPainter = remember {\n")
    sb.append(String.format(Locale.US, "    MeshGradientPainter(rows = %d, columns = %d, hasBicubicColor = false) {\n", rows - 1, cols - 1))

    meshPoints.forEachIndexed { rowIdx, row ->
      row.forEachIndexed { colIdx, point ->
        sb.append(
          String.format(
            Locale.US,
            "        setVertex(%d, %d, Offset(%.4ff, %.4ff), Color(%s))\n",
            rowIdx,
            colIdx,
            point.position.x,
            point.position.y,
            point.color.toComposeHexLiteral(),
          )
        )
      }
    }

    sb.append("    }\n")
    sb.append("}\n\n")

    return sb.toString()
  }
}
