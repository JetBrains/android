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
import androidx.compose.ui.UiComposable
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.Placeable.PlacementScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
internal fun GroupLayout(content: @Composable @UiComposable () -> Unit) {
  Layout(content) { measurables, constraints ->
    val header = measurables.first().measure(constraints)
    val rows = Row.buildList(measurables, constraints)
    val padding = 12.dp.roundToPx()

    layout(getWidth(header, rows), getHeight(header, rows, padding)) {
      header.placeRelative(0, 0)
      var y = header.height + padding

      rows.forEach {
        it.placePlaceables(this, y)
        y += it.height + padding
      }
    }
  }
}

internal object Icon

private class Row
private constructor(
  private val text: Placeable,
  private val placeable: Placeable,
  private val icon: Placeable? = null,
) {
  val width
    get() = text.width + placeable.width + (icon?.width ?: 0)

  val height
    get() = maxOf(text.height, placeable.height, icon?.height ?: 0)

  companion object {
    internal fun buildList(measurables: List<Measurable>, constraints: Constraints): Iterable<Row> {
      val i = measurables.listIterator()

      // Skip the GroupHeader
      i.next()

      return buildList {
        while (i.hasNext()) {
          add(newRow(i, constraints))
        }
      }
    }

    private fun newRow(i: ListIterator<Measurable>, constraints: Constraints): Row {
      val text = i.next().measure(constraints)
      val placeable = i.next().measure(constraints)

      if (!i.hasNext()) {
        // We're at the end and there's no Icon
        return Row(text, placeable)
      }

      val measurable = i.next()

      if (measurable.layoutId != Icon) {
        // The measurable is a Text. Go back.
        i.previous()

        return Row(text, placeable)
      }

      // We have an Icon
      return Row(text, placeable, measurable.measure(constraints))
    }
  }

  fun placePlaceables(scope: PlacementScope, y: Int) =
    with(scope) {
      var x = 0

      // Align text's baseline with placeable's
      text.placeRelative(x, y + placeable[FirstBaseline] - text[FirstBaseline])

      x += text.width
      placeable.placeRelative(x, y)

      if (icon == null) {
        return
      }

      x += placeable.width
      icon.placeRelative(x, y + (height - icon.height) / 2)
    }
}

private fun getWidth(header: Placeable, rows: Iterable<Row>): Int {
  val maxRowWidth = rows.maxOfOrNull(Row::width)
  return if (maxRowWidth == null) header.width else maxOf(header.width, maxRowWidth)
}

private fun getHeight(header: Placeable, rows: Iterable<Row>, padding: Int) =
  header.height + rows.sumOf { padding + it.height }
