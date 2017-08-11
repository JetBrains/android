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
package com.android.tools.idea.uibuilder.handlers.grid.draw

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.model.Insets
import java.awt.Graphics2D

/**
 * Decorator for GridLayout.
 * TODO: support RTL
 */
open class GridLayoutDecorator : SceneDecorator() {

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
    fun toSwingX(x: Int) = sceneContext.getSwingX(x.toFloat())
    fun toSwingY(y: Int) = sceneContext.getSwingY(y.toFloat())
    fun pxToSwing(px: Int) = (sceneContext.pxToDp(px) + 0.5f).toInt()

    val padding = retrievePaddings(component.authoritativeNlComponent)

    val left = toSwingX(component.drawX) + pxToSwing(padding.left)
    val top = toSwingY(component.drawY) + pxToSwing(padding.top)
    val right = toSwingX(component.drawX + component.drawWidth) - pxToSwing(padding.right)
    val bottom = toSwingY(component.drawY + component.drawHeight) - pxToSwing(padding.bottom)

    val children = component.children

    val rowMap = hashMapOf<Int, Int>()
    val columnMap = hashMapOf<Int, Int>()

    for (child in children) {
      val cellData = retrieveCellData(child.authoritativeNlComponent)
      val marginInsets = retrieveMargins(child.authoritativeNlComponent)

      val cellLeft = toSwingX(child.drawX) - pxToSwing(marginInsets.left)
      val cellTop = toSwingY(child.drawY) - pxToSwing(marginInsets.top)
      val cellRight = toSwingX(child.drawX + child.drawWidth) + pxToSwing(marginInsets.right)
      val cellBottom = toSwingY(child.drawY + child.drawHeight) + pxToSwing(marginInsets.bottom)

      // Don't draw the left and top edges.
      if (cellData.column != 0) {
        columnMap[cellData.column] = minOf(cellLeft, columnMap[cellData.column] ?: Int.MAX_VALUE)
      }
      if (cellData.row != 0) {
        rowMap[cellData.row] = minOf(cellTop, rowMap[cellData.row] ?: Int.MAX_VALUE)
      }

      // when row(column) span is 0, it doesn't have restricted. In this case don't use it to calculate the right(bottom) edges
      if (cellData.columnSpan != 0) {
        val rightIndex = cellData.column + cellData.columnSpan
        columnMap[rightIndex] = maxOf(cellRight, columnMap[rightIndex] ?: Int.MIN_VALUE)
      }

      if (cellData.rowSpan != 0) {
        val bottomIndex = cellData.row + cellData.rowSpan
        rowMap[bottomIndex] = maxOf(cellBottom, rowMap[bottomIndex] ?: Int.MIN_VALUE)
      }
    }

    // Draw left, top, right, and bottom edges of GridLayout
    list.add(DrawLineCommand(left, top, left, bottom))
    list.add(DrawLineCommand(left, top, right, top))
    list.add(DrawLineCommand(right, top, right, bottom))
    list.add(DrawLineCommand(left, bottom, right, bottom))

    // Draw the edges of cell. Make sure don't draw outside of GridLayout
    columnMap.values.forEach { x -> if (x in left..right) list.add(DrawLineCommand(x, top, x, bottom)) }
    rowMap.values.forEach { y -> if (y in top..bottom) list.add(DrawLineCommand(left, y, right, y)) }

    sceneContext.repaint()
  }

  internal data class CellInfo(val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int)

  /**
   * Get the [CellInfo] of the component in GridLayout.
   */
  internal open fun retrieveCellData(nlComponent: NlComponent): CellInfo {
    // By default, the (row, column, rowSpan, columnSpan) is (0, 0, 1, 1)
    return CellInfo(nlComponent.getLiveAndroidAttribute(SdkConstants.ATTR_LAYOUT_ROW)?.toIntOrNull() ?: 0,
        nlComponent.getLiveAndroidAttribute(SdkConstants.ATTR_LAYOUT_COLUMN)?.toIntOrNull() ?: 0,
        nlComponent.getLiveAndroidAttribute(SdkConstants.ATTR_LAYOUT_ROW_SPAN)?.toIntOrNull() ?: 1,
        nlComponent.getLiveAndroidAttribute(SdkConstants.ATTR_LAYOUT_COLUMN_SPAN)?.toIntOrNull() ?: 1)
  }

  /**
   * Get the padding of component by retrieving the live attributes.
   * The unit of returned [Insets] is px
   */
  internal open fun retrieveMargins(nlComponent: NlComponent): Insets = retrieveInsets(nlComponent, MARGIN_ATTRIBUTES)

  /**
   * Get the padding of component by retrieving the live attributes.
   * The unit of returned [Insets] is px
   */
  internal open fun retrievePaddings(nlComponent: NlComponent): Insets = retrieveInsets(nlComponent, PADDING_ATTRIBUTES)
}

internal data class InsetsAttributes(val all: String,
                                     val left: Pair<String, String>,
                                     val top: String,
                                     val right: Pair<String, String>,
                                     val bottom: String)

private fun NlComponent.getLiveAndroidAttribute(androidAttribute: String) = getLiveAttribute(SdkConstants.ANDROID_URI, androidAttribute)

private fun retrieveInsets(nlComponent: NlComponent, attrs: InsetsAttributes): Insets {
  val left: Int
  val top: Int
  val right: Int
  val bottom: Int

  var valueString: String? = nlComponent.getLiveAndroidAttribute(attrs.all)

  if (valueString != null) {
    val padding = getDpValue(nlComponent, valueString)
    left = padding
    top = padding
    right = padding
    bottom = padding
  }
  else {
    valueString = nlComponent.getLiveAndroidAttribute(attrs.left.first) ?: nlComponent.getLiveAndroidAttribute(attrs.left.second)
    left = getDpValue(nlComponent, valueString)

    valueString = nlComponent.getLiveAndroidAttribute(attrs.top)
    top = getDpValue(nlComponent, valueString)

    valueString = nlComponent.getLiveAndroidAttribute(attrs.right.first) ?: nlComponent.getLiveAndroidAttribute(attrs.right.second)
    right = getDpValue(nlComponent, valueString)

    valueString = nlComponent.getLiveAndroidAttribute(attrs.bottom)
    bottom = getDpValue(nlComponent, valueString)
  }
  return Insets(left, top, right, bottom)
}

/**
 * Get the value of resource string. The unit of return value is px
 * If [value] is null or illegal number format, return 0.
 */
private fun getDpValue(nlComponent: NlComponent, value: String?): Int {
  if (value != null) {
    val configuration = nlComponent.model.configuration
    val resourceResolver = configuration.resourceResolver
    if (resourceResolver != null) {
      val px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration)
      return px ?: 0
    }
  }
  return 0
}

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

internal class DrawLineCommand(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : DrawCommand {

  override fun getLevel() = DrawCommand.CLIP_LEVEL

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    g.color = sceneContext.colorSet.constraints
    g.drawLine(x1, y1, x2, y2)
  }

  override fun serialize(): String = "com.android.tools.idea.uibuilder.handlers.grid.draw.DrawLineCommand: ($x1, $y1) - ($x2, $y2)"
}
