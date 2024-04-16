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

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density

internal interface GridCellAlignment {
  /** Indicates if the alignment uses AlignmentLines. */
  val isRelative: Boolean
    get() = false

  /**
   * Returns the alignment line position relative to the left/top of the space or `null` if this
   * alignment doesn't rely on alignment lines.
   */
  fun calculateAlignmentLinePosition(placeable: Placeable): Int? = null

  fun align(size: Int, placeable: Placeable, beforeAlignmentLine: Int): Int
}

/** A simple vertical alignment that doesn't use alignment lines. */
internal class VerticalGridCellAlignment(val vertical: Alignment.Vertical) : GridCellAlignment {
  override fun align(size: Int, placeable: Placeable, beforeAlignmentLine: Int): Int {
    return vertical.align(placeable.height, size)
  }
}

/** A vertical alignment that is based on alignment lines. */
internal class VerticalGridCellAlignmentLineAlignment(val alignmentLine: AlignmentLine) :
  GridCellAlignment {
  override val isRelative: Boolean
    get() = true

  override fun calculateAlignmentLinePosition(placeable: Placeable): Int = placeable[alignmentLine]

  override fun align(size: Int, placeable: Placeable, beforeAlignmentLine: Int): Int {
    val position = calculateAlignmentLinePosition(placeable)
    return if (position != AlignmentLine.Unspecified) beforeAlignmentLine - position else 0
  }
}

internal val IntrinsicMeasurable.gridCellParentData: GridCellParentData?
  get() = parentData as? GridCellParentData

internal val Placeable.gridCellParentData: GridCellParentData?
  get() = parentData as? GridCellParentData

internal val GridCellParentData?.isRelative: Boolean
  get() = this?.verticalAlignment?.isRelative ?: false

internal class WithAlignmentLineElement(val alignmentLine: AlignmentLine) :
  ModifierNodeElement<SiblingsAlignedNode.WithAlignmentLineNode>() {
  override fun create(): SiblingsAlignedNode.WithAlignmentLineNode {
    return SiblingsAlignedNode.WithAlignmentLineNode(alignmentLine)
  }

  override fun update(node: SiblingsAlignedNode.WithAlignmentLineNode) {
    node.alignmentLine = alignmentLine
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "alignBy"
    value = alignmentLine
  }

  override fun hashCode(): Int = alignmentLine.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    val otherModifier = other as? WithAlignmentLineElement ?: return false
    return alignmentLine == otherModifier.alignmentLine
  }
}

internal sealed class SiblingsAlignedNode : ParentDataModifierNode, Modifier.Node() {
  abstract override fun Density.modifyParentData(parentData: Any?): Any?

  internal class WithAlignmentLineNode(var alignmentLine: AlignmentLine) : SiblingsAlignedNode() {
    override fun Density.modifyParentData(parentData: Any?): Any {
      return ((parentData as? GridCellParentData) ?: GridCellParentData()).also {
        it.verticalAlignment = VerticalGridCellAlignmentLineAlignment(alignmentLine)
      }
    }
  }
}

internal class HorizontalAlignElement(val alignment: Alignment.Horizontal) :
  ModifierNodeElement<HorizontalAlignNode>() {
  override fun create(): HorizontalAlignNode {
    return HorizontalAlignNode(alignment)
  }

  override fun update(node: HorizontalAlignNode) {
    node.alignment = alignment
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "align"
    value = alignment
  }

  override fun hashCode(): Int = alignment.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    val otherModifier = other as? HorizontalAlignElement ?: return false
    return alignment == otherModifier.alignment
  }
}

internal class HorizontalAlignNode(var alignment: Alignment.Horizontal) :
  ParentDataModifierNode, Modifier.Node() {
  override fun Density.modifyParentData(parentData: Any?): GridCellParentData {
    return ((parentData as? GridCellParentData) ?: GridCellParentData()).also {
      it.horizontalAlignment = alignment
    }
  }
}

internal class VerticalAlignElement(val alignment: Alignment.Vertical) :
  ModifierNodeElement<VerticalAlignNode>() {
  override fun create(): VerticalAlignNode {
    return VerticalAlignNode(alignment)
  }

  override fun update(node: VerticalAlignNode) {
    node.alignment = alignment
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "align"
    value = alignment
  }

  override fun hashCode(): Int = alignment.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    val otherModifier = other as? VerticalAlignElement ?: return false
    return alignment == otherModifier.alignment
  }
}

internal class VerticalAlignNode(var alignment: Alignment.Vertical) :
  ParentDataModifierNode, Modifier.Node() {
  override fun Density.modifyParentData(parentData: Any?): GridCellParentData {
    return ((parentData as? GridCellParentData) ?: GridCellParentData()).also {
      it.verticalAlignment = VerticalGridCellAlignment(alignment)
    }
  }
}

/**
 * Parent data associated with children.
 *
 * Cells can be aligned vertically by alignment lines.
 */
internal data class GridCellParentData(
  var horizontalAlignment: Alignment.Horizontal? = null,
  var verticalAlignment: GridCellAlignment? = null,
)
