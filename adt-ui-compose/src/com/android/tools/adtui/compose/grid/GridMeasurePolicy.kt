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
package com.android.tools.adtui.compose.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Measures and places composables in a Grid. Intrinsic sizes are used to allocate space between the
 * rows and columns before measurement to ensure that later rows / columns have their constraints
 * considered.
 */
internal data class GridMeasurePolicy(
  private val horizontalArrangement: Arrangement.Horizontal,
  private val verticalArrangement: Arrangement.Vertical,
  private val horizontalAlignment: Alignment.Horizontal,
  private val verticalAlignment: Alignment.Vertical,
) : MultiContentMeasurePolicy {
  override fun MeasureScope.measure(
    measurables: List<List<Measurable>>,
    constraints: Constraints,
  ): MeasureResult {
    val minWidth = constraints.minWidth
    val minHeight = constraints.minHeight

    val horizontalArrangementSpacingPx = horizontalArrangement.spacing.roundToPx()
    val verticalArrangementSpacingPx = verticalArrangement.spacing.roundToPx()
    var totalVerticalSpacingPx = 0

    val columnCount: Int = measurables.maxOfOrNull { it.size } ?: 0
    val rowCount = measurables.size

    val columnSizes = IntArray(columnCount)
    val rowSizes = IntArray(rowCount)

    val placeables = Array<Array<Placeable?>>(rowCount) { Array(columnCount) { null } }

    val beforeVerticalAlignmentLine = IntArray(rowCount)
    val afterVerticalAlignmentLine = IntArray(rowCount)

    computeSizesFromIntrinsics(
      { column -> (0 until rowCount).mapNotNull { measurables[it].getOrNull(column) } },
      { constraints.maxHeight },
      IntrinsicMeasurable::minIntrinsicWidth,
      IntrinsicMeasurable::maxIntrinsicWidth,
      horizontalArrangementSpacingPx,
      constraints.maxWidth,
      columnSizes,
      whenOverconstrained = WhenOverconstrained.Compress,
    )

    computeSizesFromIntrinsics(
      { row -> measurables[row] },
      columnSizes::get,
      IntrinsicMeasurable::minIntrinsicHeight,
      IntrinsicMeasurable::maxIntrinsicHeight,
      verticalArrangementSpacingPx,
      constraints.maxHeight,
      rowSizes,
      whenOverconstrained = WhenOverconstrained.Truncate,
    )

    var remainingHeight = constraints.maxHeight
    for (rowIndex in measurables.indices) {
      var anyAlignBy = false

      rowSizes[rowIndex] = rowSizes[rowIndex].coerceAtMost(remainingHeight)

      for (columnIndex in measurables[rowIndex].indices) {
        val parentData = measurables[rowIndex][columnIndex].gridCellParentData
        anyAlignBy = anyAlignBy || parentData.isRelative

        val placeable =
          measurables[rowIndex][columnIndex].measure(
            Constraints(
              minWidth = 0,
              maxWidth = columnSizes[columnIndex],
              minHeight = 0,
              maxHeight = rowSizes[rowIndex],
            )
          )
        placeables[rowIndex][columnIndex] = placeable
      }

      // If there are alignment lines, figure out where they go, and adjust the height of the row if
      // needed.
      if (anyAlignBy) {
        for (placeable in placeables[rowIndex]) {
          placeable ?: continue
          val parentData = placeable.gridCellParentData ?: continue
          val alignmentLinePosition =
            parentData.verticalAlignment?.calculateAlignmentLinePosition(placeable) ?: continue
          beforeVerticalAlignmentLine[rowIndex] =
            max(
              beforeVerticalAlignmentLine[rowIndex],
              if (alignmentLinePosition != AlignmentLine.Unspecified) alignmentLinePosition else 0,
            )
          afterVerticalAlignmentLine[rowIndex] =
            max(
              afterVerticalAlignmentLine[rowIndex],
              placeable.height -
                if (alignmentLinePosition != AlignmentLine.Unspecified) {
                  alignmentLinePosition
                } else {
                  placeable.height
                },
            )
        }
        rowSizes[rowIndex] =
          max(
            rowSizes[rowIndex],
            (beforeVerticalAlignmentLine[rowIndex] + afterVerticalAlignmentLine[rowIndex])
              .coerceAtMost(remainingHeight),
          )
      }

      remainingHeight -= rowSizes[rowIndex]
      if (rowIndex < rowCount - 1) {
        val rowSpacing = verticalArrangementSpacingPx.coerceAtMost(remainingHeight)
        remainingHeight -= rowSpacing
        totalVerticalSpacingPx += rowSpacing
      }
    }

    // Compute the Row or Column size and position the children.
    val horizontalSize =
      max(columnSizes.sum() + horizontalArrangementSpacingPx * (columnCount - 1), minWidth)
    val verticalSize = max(rowSizes.sum() + totalVerticalSpacingPx, minHeight)

    val horizontalPositions = IntArray(columnCount)
    val verticalPositions = IntArray(rowCount)

    with(horizontalArrangement) {
      arrange(horizontalSize, columnSizes, layoutDirection, horizontalPositions)
    }
    with(verticalArrangement) { arrange(verticalSize, rowSizes, verticalPositions) }
    return layout(horizontalSize, verticalSize) {
      placeables.forEachIndexed { y, row ->
        row.forEachIndexed { x, placeable ->
          placeable ?: return@forEachIndexed
          val horizontalAlignment =
            placeable.gridCellParentData?.horizontalAlignment ?: horizontalAlignment
          val verticalAlignment =
            placeable.gridCellParentData?.verticalAlignment
              ?: VerticalGridCellAlignment(verticalAlignment)
          val offsetX = horizontalAlignment.align(placeable.width, columnSizes[x], layoutDirection)
          val offsetY =
            verticalAlignment.align(rowSizes[y], placeable, beforeVerticalAlignmentLine[y])
          placeable.place(horizontalPositions[x] + offsetX, verticalPositions[y] + offsetY)
        }
      }
    }
  }
}

private enum class WhenOverconstrained {
  Truncate,
  Compress,
}

/**
 * Computes either the widths or heights of a grid of Measurables based on their intrinsic sizes.
 *
 * @param measurables the Measurables to measure, provided as a function from indices of
 *   [sizesOutput] to the collection of Measurables that make up the corresponding row or column
 * @param constraints a function from indices of [sizesOutput] to the max constraint for computing
 *   the intrinsic size of the corresponding row or column
 * @param minIntrinsic a reference to the minIntrinsicWidth or minIntrinsicHeight function
 * @param maxIntrinsic a reference to the maxIntrinsicWidth or maxIntrinsicHeight function
 * @param spacing the spacing between elements
 * @param maxSize the maximum size available for all elements combined
 * @param sizesOutput an array to store the computed sizes
 * @param whenOverconstrained determines how to handle situations where the total intrinsic size
 *   exceeds the available [maxSize].
 */
private fun computeSizesFromIntrinsics(
  measurables: (Int) -> Iterable<Measurable>,
  constraints: (Int) -> Int,
  minIntrinsic: IntrinsicMeasurable.(Int) -> Int,
  maxIntrinsic: IntrinsicMeasurable.(Int) -> Int,
  spacing: Int,
  maxSize: Int,
  sizesOutput: IntArray,
  whenOverconstrained: WhenOverconstrained = WhenOverconstrained.Truncate,
) {
  val minIntrinsicSizes = IntArray(sizesOutput.size)
  val maxIntrinsicSizes = IntArray(sizesOutput.size)

  for (index in 0 until sizesOutput.size) {
    minIntrinsicSizes[index] =
      measurables(index).mapIndexed { i, m -> m.minIntrinsic(constraints(i)) }.maxOrNull() ?: 0
    maxIntrinsicSizes[index] =
      measurables(index).mapIndexed { i, m -> m.maxIntrinsic(constraints(i)) }.maxOrNull() ?: 0
  }

  val totalMinIntrinsic = minIntrinsicSizes.sum()
  val totalMaxIntrinsic = maxIntrinsicSizes.sum()

  // How much space we have beyond the minimum intrinsic size (or how much we're lacking)
  val extraWidth = maxSize - totalMinIntrinsic - spacing * (sizesOutput.size - 1)
  // How much total flex we have in our intrinsic sizes
  val flex = totalMaxIntrinsic - totalMinIntrinsic
  // Determine what fraction of the flex we have space for; allocate space proportionally to
  // each component's flex.
  val flexRatio =
    if (flex > 0 && extraWidth > 0) (extraWidth / flex.toFloat()).coerceAtMost(1f) else 0f

  val deficit =
    when (whenOverconstrained) {
      // Keep each component at its min size; let the measure policy constrain the space allocated.
      WhenOverconstrained.Truncate -> 0
      // Take away space proportionally to each component's min size.
      WhenOverconstrained.Compress -> extraWidth.coerceAtMost(0)
    }

  for (index in sizesOutput.indices) {
    sizesOutput[index] =
      (minIntrinsicSizes[index] +
          flexRatio * (maxIntrinsicSizes[index] - minIntrinsicSizes[index]) +
          deficit * minIntrinsicSizes[index] / totalMinIntrinsic)
        .roundToInt()
  }
}
