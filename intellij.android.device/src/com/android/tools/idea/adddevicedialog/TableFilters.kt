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
package com.android.tools.idea.adddevicedialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.HideablePanel
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text

/**
 * An attribute of a table row that can be extracted and used for filtering; it may or may not be
 * represented in a column. [V] must have a toString() method that is suitable for display.
 */
internal class RowAttribute<T, V>(
  val name: String,
  val comparator: Comparator<V>,
  val value: (T) -> V,
)

/**
 * A Composable that puts a list of checkboxes in a HideablePanel and tracks their selection state.
 */
@Composable
internal fun <V> SetFilter(
  header: String,
  values: List<V>,
  selection: MutableMap<V, Boolean>,
  modifier: Modifier = Modifier,
) {
  HideablePanel(header, modifier.padding(6.dp)) {
    Column {
      for (item in values) {
        CheckboxRow(selection[item] == true, onCheckedChange = { selection[item] = it }) {
          Text(item.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
internal fun <V> SetFilter(state: SetFilterState<*, V>, modifier: Modifier = Modifier) {
  SetFilter(
    state.attribute.name,
    state.selection.keys.sortedWith(state.attribute.comparator),
    state.selection,
    modifier,
  )
}

interface RowFilter<T> {
  fun apply(row: T): Boolean
}

/** The UI state for a single set-based attribute filter. */
internal class SetFilterState<T, V>(
  val attribute: RowAttribute<T, V>,
  val selection: SnapshotStateMap<V, Boolean>,
) : RowFilter<T> {
  override fun apply(row: T) = selection[attribute.value(row)] == true
}

/**
 * Produces an initial SetFilterState, containing entries for all the distinct values found in the
 * given rows, with all values enabled.
 */
internal fun <T, V> RowAttribute<T, V>.initialSetFilterState(rows: List<T>) =
  SetFilterState(this, rows.map { Pair(value(it), true) }.toMutableStateMap())
