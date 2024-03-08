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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import kotlin.math.max

@Composable
internal fun GroupLayout(content: @Composable @UiComposable () -> Unit) {
  Layout(content) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val header = placeables[0]
    val rows = Row.buildList(placeables)

    layout(getWidth(header, rows), header.height + rows.sumOf(Row::height)) {
      header.placeRelative(0, 0)
      var y = header.height

      rows.forEach {
        it.text.placeRelative(0, y)
        it.placeable.placeRelative(it.text.width, y)

        y += it.height
      }
    }
  }
}

private class Row private constructor(val text: Placeable, val placeable: Placeable) {
  val width
    get() = text.width + placeable.width

  val height
    get() = max(text.height, placeable.height)

  companion object {
    internal fun buildList(placeables: List<Placeable>) = buildList {
      for (i in 1 until placeables.size step 2) {
        add(Row(placeables[i], placeables[i + 1]))
      }
    }
  }
}

private fun getWidth(header: Placeable, rows: Iterable<Row>): Int {
  val maxRowWidth = rows.maxOfOrNull(Row::width)
  return if (maxRowWidth == null) header.width else max(header.width, maxRowWidth)
}
