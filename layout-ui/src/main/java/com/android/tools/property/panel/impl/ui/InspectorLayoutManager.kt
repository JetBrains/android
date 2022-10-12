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
package com.android.tools.property.panel.impl.ui

import com.android.tools.property.ptable.ColumnFraction
import com.intellij.util.ui.JBDimension
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2

enum class Placement {LEFT, RIGHT, LINE}

private const val MIN_WIDTH = 120
private const val MIN_HEIGHT = 240

/**
 * Layout for 2 column grid used in [InspectorPanelImpl].
 */
class InspectorLayoutManager(private val nameColumnFraction: ColumnFraction = ColumnFraction()): LayoutManager2 {
  private var leftWidth = -1
  private var rightWidth = -1
  private var totalHeight = -1
  private val rowHeights = IntArrayList(50)
  private val placementMap = HashMap<Component, Placement>()
  private var lastAdded = Placement.LINE

  override fun invalidateLayout(container: Container) {
    invalidateLayout()
  }

  override fun layoutContainer(container: Container) {
    computePreferredGridSize(container)
    val insets = container.insets
    val size = container.size
    val width = size.width - insets.left - insets.right
    val leftMargin = insets.left
    val left = maxOf((width * nameColumnFraction.value).toInt(), 0)
    val right = maxOf(width - left, 0)
    var rowIndex = 0
    var y = insets.top

    // Note: Do NOT iterate over container.components() since that call would allocate an array
    for (index in 0 until container.componentCount) {
      val component = container.getComponent(index)
      if (component.isVisible) {
        val placement = placementMap.getOrDefault(component, Placement.LINE)
        val height = if (rowIndex < rowHeights.size) rowHeights.getInt(rowIndex) else component.preferredSize.height
        when (placement) {
          Placement.LEFT -> component.setBounds(leftMargin, y, left, height)
          Placement.RIGHT -> component.setBounds(leftMargin + left, y, right, height)
          Placement.LINE -> component.setBounds(leftMargin, y, width, height)
        }
        if (placement != Placement.LEFT) {
          rowIndex++
          y += height
        }
      }
    }
  }

  override fun getLayoutAlignmentY(container: Container) = 0.5f

  override fun getLayoutAlignmentX(container: Container) = 0.5f

  override fun maximumLayoutSize(container: Container) = MAXIMUM_LAYOUT_SIZE

  override fun preferredLayoutSize(container: Container): Dimension {
    computePreferredGridSize(container)
    return Dimension(leftWidth + rightWidth, totalHeight)
  }

  override fun minimumLayoutSize(container: Container) = MINIMUM_LAYOUT_SIZE

  override fun addLayoutComponent(component: Component, place: Any?) {
    val placement = place as? Placement ?: Placement.LINE
    require(!(lastAdded == Placement.LEFT && placement != Placement.RIGHT)) { "Expected a right side component" }
    require(!(lastAdded != Placement.LEFT && placement == Placement.RIGHT)) { "Expected a left side component" }
    placementMap.put(component, placement)
    invalidateLayout()
    lastAdded = placement
  }

  override fun addLayoutComponent(label: String, component: Component) {
    addLayoutComponent(component, Placement.LINE)
  }

  override fun removeLayoutComponent(component: Component) {
    placementMap.remove(component)
    invalidateLayout()
  }

  fun removeAll() {
    lastAdded = Placement.LINE
    placementMap.clear()
    invalidateLayout()
  }

  fun getRowHeight(component: Component): Int {
    return maxOf(component.height, otherComponentHeight(component))
  }

  /**
   * Return the height of the "other" component in a line that contains [component].
   *
   * @return the height of the component to the right [index + 1] of a component that is placed to the left
   *         the height of the component to the left [index - 1] of a component that is placed to the right
   *         -1 if this component takes the entire space in the line
   */
  private fun otherComponentHeight(component: Component): Int {
    val container = component.parent
    val placement = placementMap.getOrDefault(component, Placement.LINE)
    return when (placement) {
      Placement.LINE -> -1
      Placement.LEFT -> {
        val index = indexOf(container, component)
        if (index >= 0 && index + 1 < container.componentCount) container.getComponent(index + 1).height else -1
      }
      Placement.RIGHT -> {
        val index = indexOf(container, component)
        if (index > 0) container.getComponent(index - 1).height else -1
      }
    }
  }

  private fun indexOf(container: Container, component: Component): Int {
    // Note: Do NOT iterate over container.components() since that call would allocate an array
    for (index in 0 until container.componentCount) {
      val current = container.getComponent(index)
      if (component == current) {
        return index
      }
    }
    return -1
  }

  private fun invalidateLayout() {
    leftWidth = -1
    rightWidth = -1
    totalHeight = -1
    rowHeights.clear()
  }

  /**
   * Compute the size of the grid.
   *
   * Go through all components and compute the width of the widest line and the
   * sum of the heights of all lines. For the width computation take the wanted
   * fractions into account (ideally the labels are 40% of the width).
   */
  private fun computePreferredGridSize(container: Container) {
    if (totalHeight >= 0) return
    require(lastAdded != Placement.LEFT) { "Expected a right side component" }

    var rowHeight = 0
    var maxHeight = 0
    var leftMaxWidth = 0
    var rightMaxWidth = 0
    var lineMaxWidth = 0
    rowHeights.clear()

    // Note: Do NOT iterate over container.components() since that call would allocate an array
    for (index in 0 until container.componentCount) {
      val component = container.getComponent(index)
      if (component.isVisible) {
        val size = component.preferredSize
        val placement = placementMap.getOrDefault(component, Placement.LINE)
        when (placement) {
          Placement.LINE -> lineMaxWidth = maxOf(lineMaxWidth, size.width)
          Placement.LEFT -> leftMaxWidth = maxOf(leftMaxWidth, size.width)
          Placement.RIGHT -> rightMaxWidth = maxOf(rightMaxWidth, size.width)
        }
        rowHeight = maxOf(rowHeight, size.height)
        if (placement != Placement.LEFT) {
          rowHeights.add(rowHeight)
          maxHeight += rowHeight
          rowHeight = 0
        }
      }
    }

    val rightFraction = 1.0f - nameColumnFraction.value
    val width = maxOf(leftMaxWidth / nameColumnFraction.value, rightMaxWidth / rightFraction, lineMaxWidth.toFloat()).toInt()
    leftWidth = (width * nameColumnFraction.value).toInt()
    rightWidth = width - leftWidth
    totalHeight = maxHeight
  }

  companion object {
    private val MINIMUM_LAYOUT_SIZE = JBDimension(MIN_WIDTH, MIN_HEIGHT)
    private val MAXIMUM_LAYOUT_SIZE = Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)
  }
}
