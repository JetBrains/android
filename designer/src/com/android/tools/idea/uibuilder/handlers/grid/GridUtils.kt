/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.grid

import com.android.SdkConstants
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.model.Insets

/**
 * data for record the information of barriers of GridLayout
 */
class GridBarriers(@AndroidDpCoordinate val left: Int,
                   @AndroidDpCoordinate val top: Int,
                   @AndroidDpCoordinate val right: Int,
                   @AndroidDpCoordinate val bottom: Int,
                   @AndroidDpCoordinate val rows: Array<Int>,
                   @AndroidDpCoordinate val columns: Array<Int>)

/**
 * Function for getting Barriers of
 */
fun getGridBarriers(gridComponent: SceneComponent): GridBarriers {
  val isSupportLibrary = SdkConstants.GRID_LAYOUT_V7.isEquals(gridComponent.nlComponent.tagName)

  // Helper function to convert px to dp
  fun Int.toDp() = Coordinates.pxToDp(gridComponent.scene.designSurface, this)

  @AndroidCoordinate val padding = retrievePaddings(gridComponent.authoritativeNlComponent)

  val left = gridComponent.drawX + padding.left.toDp()
  val top = gridComponent.drawY + padding.top.toDp()
  val right = gridComponent.drawX + gridComponent.drawWidth - padding.right.toDp()
  val bottom = gridComponent.drawY + gridComponent.drawHeight - padding.bottom.toDp()

  val children = gridComponent.children

  val rowMap = hashMapOf<Int, Int>()
  val columnMap = hashMapOf<Int, Int>()

  var previousRow = 0
  // to make first undefined column component can locate at (previousRow, 0), the initial value of previousColumn should be -1
  var previousColumn = -1

  for (child in children) {
    val cellData = retrieveCellData(child.authoritativeNlComponent, isSupportLibrary)
    if (cellData.column == UNDEFINED_COLUMN) {
      cellData.column = previousColumn + 1
    }
    if (cellData.row == UNDEFINED_ROW) {
      cellData.row = previousRow
    }

    @AndroidCoordinate val marginInsets = retrieveMargins(child.authoritativeNlComponent)

    val cellLeft = child.drawX - marginInsets.left.toDp()
    val cellTop = child.drawY - marginInsets.top.toDp()
    val cellRight = child.drawX + child.drawWidth + marginInsets.right.toDp()
    val cellBottom = child.drawY + child.drawHeight + marginInsets.bottom.toDp()

    // Avoid drawing the left edge of GridLayout
    if (cellData.column != 0) {
      columnMap[cellData.column] = minOf(cellLeft, columnMap[cellData.column] ?: Int.MAX_VALUE)
    }

    // Avoid drawing the top edge of GridLayout
    if (cellData.row != 0) {
      rowMap[cellData.row] = minOf(cellTop, rowMap[cellData.row] ?: Int.MAX_VALUE)
    }

    // when row(column) span is 0, it doesn't have restricted. In this case don't use it to calculate the bottom(right) edges
    if (cellData.column != -1 && cellData.columnSpan != 0) {
      val rightIndex = cellData.column + cellData.columnSpan
      columnMap[rightIndex] = maxOf(cellRight, columnMap[rightIndex] ?: Int.MIN_VALUE)
    }

    if (cellData.row != -1 && cellData.rowSpan != 0) {
      val bottomIndex = cellData.row + cellData.rowSpan
      rowMap[bottomIndex] = maxOf(cellBottom, rowMap[bottomIndex] ?: Int.MIN_VALUE)
    }

    previousRow = cellData.row
    previousColumn = cellData.column
  }

  val columnCount = columnMap.keys.max()?.plus(1) ?: 1
  val rowCount = rowMap.keys.max()?.plus(1) ?: 1

  columnMap[0] = left
  columnMap[columnCount] = right

  rowMap[0] = top
  rowMap[rowCount] = bottom

  val columnArray: Array<Int> = Array(columnCount + 1) { columnMap[it] ?: Int.MIN_VALUE }
  val rowArray: Array<Int> = Array(rowCount + 1) { rowMap[it] ?: Int.MIN_VALUE }

  return GridBarriers(left, top, right, bottom, rowArray, columnArray)
}

private const val UNDEFINED_ROW = -1
private const val UNDEFINED_COLUMN = -1

/**
 * Class for record the cell attributes.
 * If the row/column is not defined, the value would be [UNDEFINED_ROW]/[UNDEFINED_COLUMN]
 * If the rowSpan/columnSpan is not defined, the value would be 0 as the default value in Android framework.
 */
private class CellInfo(var row: Int, var column: Int, val rowSpan: Int, val columnSpan: Int)

/**
 * Get the [CellInfo] of the component in GridLayout.
 * @see [CellInfo]
 */
private fun retrieveCellData(nlComponent: NlComponent, isSupportLibrary: Boolean): CellInfo {
  val namespace = if (isSupportLibrary) SdkConstants.AUTO_URI else SdkConstants.ANDROID_URI
  val getAttribute: (String, Int) -> Int = { name, defaultValue ->
    nlComponent.getLiveAttribute(namespace, name)?.toIntOrNull() ?: defaultValue
  }

  // If the span is not defined, the default (rowSpan, columnSpan) is (1, 1)
  // If the row and column is not defined, the value of (row, column) is (row of previousComponent, column of previousComponent + 1)
  return CellInfo(getAttribute(SdkConstants.ATTR_LAYOUT_ROW, UNDEFINED_ROW),
      getAttribute(SdkConstants.ATTR_LAYOUT_COLUMN, UNDEFINED_COLUMN),
      getAttribute(SdkConstants.ATTR_LAYOUT_ROW_SPAN, 1),
      getAttribute(SdkConstants.ATTR_LAYOUT_COLUMN_SPAN, 1))
}

private data class InsetsAttributes(val all: String,
                                     val left: Pair<String, String>,
                                     val top: String,
                                     val right: Pair<String, String>,
                                     val bottom: String)

private fun NlComponent.getLiveAndroidAttribute(androidAttribute: String) = getLiveAttribute(SdkConstants.ANDROID_URI, androidAttribute)

/**
 * Get the padding of component by retrieving the live attributes.
 * The unit of returned [Insets] is px
 */
@AndroidCoordinate
private fun retrieveMargins(nlComponent: NlComponent): Insets = retrieveInsets(nlComponent, MARGIN_ATTRIBUTES)

/**
 * Get the padding of component by retrieving the live attributes.
 * The unit of returned [Insets] is px
 */
@AndroidCoordinate
private fun retrievePaddings(nlComponent: NlComponent): Insets = retrieveInsets(nlComponent, PADDING_ATTRIBUTES)

private val PADDING_ATTRIBUTES = InsetsAttributes(
    SdkConstants.ATTR_PADDING,
    Pair(SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_LEFT),
    SdkConstants.ATTR_PADDING_TOP,
    Pair(SdkConstants.ATTR_PADDING_END, SdkConstants.ATTR_PADDING_RIGHT),
    SdkConstants.ATTR_PADDING_BOTTOM)

private val MARGIN_ATTRIBUTES = InsetsAttributes(
    SdkConstants.ATTR_LAYOUT_MARGIN,
    Pair(SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT),
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    Pair(SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT),
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM)

@AndroidCoordinate
private fun retrieveInsets(nlComponent: NlComponent, attrs: InsetsAttributes): Insets {
  val left: Int
  val top: Int
  val right: Int
  val bottom: Int

  var valueString: String? = nlComponent.getLiveAndroidAttribute(attrs.all)

  if (valueString != null) {
    val padding = getPxValue(nlComponent, valueString)
    left = padding
    top = padding
    right = padding
    bottom = padding
  }
  else {
    valueString = nlComponent.getLiveAndroidAttribute(attrs.left.first) ?: nlComponent.getLiveAndroidAttribute(attrs.left.second)
    left = getPxValue(nlComponent, valueString)

    valueString = nlComponent.getLiveAndroidAttribute(attrs.top)
    top = getPxValue(nlComponent, valueString)

    valueString = nlComponent.getLiveAndroidAttribute(attrs.right.first) ?: nlComponent.getLiveAndroidAttribute(attrs.right.second)
    right = getPxValue(nlComponent, valueString)

    valueString = nlComponent.getLiveAndroidAttribute(attrs.bottom)
    bottom = getPxValue(nlComponent, valueString)
  }
  return Insets(left, top, right, bottom)
}

/**
 * Get the value of resource string. The unit of return value is px
 * If [value] is null or illegal number format, return 0.
 */
@AndroidCoordinate
private fun getPxValue(nlComponent: NlComponent, value: String?): Int {
  if (value != null) {
    val configuration = nlComponent.model.configuration
    val resourceResolver = configuration.resourceResolver
    if (resourceResolver != null) {
      return ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration) ?: 0
    }
  }
  return 0
}
