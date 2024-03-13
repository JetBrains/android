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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.Placeable.PlacementScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints

@Composable
internal fun GroupLayout(
  modifier: Modifier = Modifier,
  content: @Composable @UiComposable () -> Unit,
) {
  Layout(content, modifier) { measurables, constraints ->
    val relatedPadding = Padding.SMALL.roundToPx()
    val unrelatedPadding = Padding.MEDIUM.roundToPx()

    val header = measurables.first().measure(constraints)
    val rows = Row.buildRows(measurables, constraints, relatedPadding, unrelatedPadding)
    val maxTextWidth = rows.maxOf { it.text.width }

    layout(getWidth(header, rows, maxTextWidth), getHeight(header, rows, unrelatedPadding)) {
      header.placeRelative(0, 0)
      var y = header.height + unrelatedPadding

      rows.forEach {
        it.placePlaceables(this, y, maxTextWidth)
        y += it.height + unrelatedPadding
      }
    }
  }
}

internal object Icon

private class Row
private constructor(
  val text: Placeable,
  private val placeable: Placeable,
  private val icon: Placeable? = null,
  private val relatedPadding: Int,
  private val unrelatedPadding: Int,
) {
  val height
    get() = maxOf(text.height, placeable.height, icon?.height ?: 0)

  companion object {
    internal fun buildRows(
      measurables: List<Measurable>,
      constraints: Constraints,
      relatedPadding: Int,
      unrelatedPadding: Int,
    ): Iterable<Row> {
      val i = measurables.listIterator()

      // Skip the GroupHeader
      i.next()

      return buildList {
        while (i.hasNext()) {
          add(newRow(i, constraints, relatedPadding, unrelatedPadding))
        }
      }
    }

    private fun newRow(
      i: ListIterator<Measurable>,
      constraints: Constraints,
      relatedPadding: Int,
      unrelatedPadding: Int,
    ): Row {
      val text = i.next().measure(constraints)
      val placeable = i.next().measure(constraints)

      if (!i.hasNext()) {
        // We're at the end and there's no Icon
        return Row(
          text,
          placeable,
          relatedPadding = relatedPadding,
          unrelatedPadding = unrelatedPadding,
        )
      }

      val measurable = i.next()

      if (measurable.layoutId != Icon) {
        // The measurable is a Text. Go back.
        i.previous()

        return Row(
          text,
          placeable,
          relatedPadding = relatedPadding,
          unrelatedPadding = unrelatedPadding,
        )
      }

      // We have an Icon
      return Row(text, placeable, measurable.measure(constraints), relatedPadding, unrelatedPadding)
    }
  }

  fun width(maxTextWidth: Int): Int {
    val width = maxTextWidth + relatedPadding + placeable.width
    return if (icon == null) width else width + unrelatedPadding + icon.width
  }

  fun placePlaceables(scope: PlacementScope, y: Int, maxTextWidth: Int) =
    with(scope) {
      var x = 0

      // Align text's baseline with placeable's
      text.placeRelative(x, y + placeable[FirstBaseline] - text[FirstBaseline])

      x += maxTextWidth + relatedPadding
      placeable.placeRelative(x, y)

      if (icon == null) {
        return
      }

      x += placeable.width + unrelatedPadding
      icon.placeRelative(x, y + (height - icon.height) / 2)
    }
}

private fun getWidth(header: Placeable, rows: Iterable<Row>, maxTextWidth: Int) =
  maxOf(header.width, rows.maxOf { it.width(maxTextWidth) })

private fun getHeight(header: Placeable, rows: Iterable<Row>, padding: Int) =
  header.height + rows.sumOf { padding + it.height }
