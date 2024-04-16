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
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout

/**
 * A layout that positions elements in a two-dimensional grid. Row and column sizes are determined
 * by the measurements of the cell contents. Space is allocated in a left to right, top to bottom
 * order.
 *
 * @param modifier The modifier to be applied to the Grid.
 * @param horizontalArrangement The horizontal arrangement of the layout's children.
 * @param verticalArrangement The horizontal arrangement of the layout's children.
 * @param horizontalAlignment The default horizontal alignment of the layout's children; may be
 *   overridden by [GridRowScope.align] modifiers.
 * @param verticalAlignment The default vertical alignment of the layout's children; may be
 *   overridden by [GridRowScope.align], [GridRowScope.alignBy], or [GridRowScope.alignByBaseline]
 *   modifiers.
 */
@Composable
fun Grid(
  modifier: Modifier = Modifier,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  verticalAlignment: Alignment.Vertical = Alignment.Top,
  content: GridScope.() -> Unit,
) {
  val measurePolicy =
    remember(horizontalArrangement, verticalArrangement, horizontalAlignment, verticalAlignment) {
      GridMeasurePolicy(
        horizontalArrangement,
        verticalArrangement,
        horizontalAlignment,
        verticalAlignment,
      )
    }
  val gridScope = GridScopeImpl()
  with(gridScope) { content() }
  Layout(contents = gridScope.rows, measurePolicy = measurePolicy, modifier = modifier)
}

interface GridScope {
  fun GridRow(row: @Composable GridRowScope.() -> Unit)
}

internal class GridScopeImpl : GridScope {
  internal val rows = mutableListOf<@Composable () -> Unit>()

  override fun GridRow(row: @Composable GridRowScope.() -> Unit) {
    rows.add({ row.invoke(GridRowScopeInstance) })
  }
}

/** Scope for the children of GridRow. */
@LayoutScopeMarker
@Immutable
interface GridRowScope {
  /**
   * Align the element horizontally within the grid cell. This alignment will have priority over the
   * [Grid]'s `horizontalAlignment` parameter.
   */
  @Stable fun Modifier.align(alignment: Alignment.Horizontal): Modifier

  /**
   * Align the element vertically within the grid cell. This alignment will have priority over the
   * [Grid]'s `verticalAlignment` parameter.
   */
  @Stable fun Modifier.align(alignment: Alignment.Vertical): Modifier

  /**
   * Position the element vertically such that its [alignmentLine] aligns with sibling elements also
   * configured to [alignBy]. [alignBy] is a form of [align], so both modifiers will not work
   * together if specified for the same layout. [alignBy] can be used to align two layouts by
   * baseline inside a GridRow, using `alignBy(FirstBaseline)`. Within a GridRow, all components
   * with [alignBy] will align vertically using the specified [HorizontalAlignmentLine]s or values
   * provided using the other [alignBy] overload, forming a sibling group. At least one element of
   * the sibling group will be placed as it had [Alignment.Top] align in GridRow, and the alignment
   * of the other siblings will be then determined such that the alignment lines coincide. Note that
   * if only one element in a GridRow has the [alignBy] modifier specified the element will be
   * positioned as if it had [Alignment.Top] align.
   *
   * @see alignByBaseline
   */
  @Stable fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier

  /**
   * Position the element vertically such that its first baseline aligns with sibling elements also
   * configured to [alignByBaseline] or [alignBy]. This modifier is a form of [align], so both
   * modifiers will not work together if specified for the same layout. [alignByBaseline] is a
   * particular case of [alignBy]. See [alignBy] for more details.
   *
   * @see alignBy
   */
  @Stable fun Modifier.alignByBaseline(): Modifier
}

internal object GridRowScopeInstance : GridRowScope {
  @Stable
  override fun Modifier.align(alignment: Alignment.Horizontal) =
    this.then(HorizontalAlignElement(alignment))

  @Stable
  override fun Modifier.align(alignment: Alignment.Vertical) =
    this.then(VerticalAlignElement(alignment))

  @Stable
  override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine) =
    this.then(WithAlignmentLineElement(alignmentLine = alignmentLine))

  @Stable override fun Modifier.alignByBaseline() = alignBy(FirstBaseline)
}
