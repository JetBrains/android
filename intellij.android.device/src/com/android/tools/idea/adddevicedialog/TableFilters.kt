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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.HideablePanel
import java.util.TreeSet
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.items

/**
 * An attribute of a table row that can be extracted and used for filtering; it may or may not be
 * represented in a column. [V] must have a toString() method that is suitable for display.
 */
class RowAttribute<T, V>(val name: String, val comparator: Comparator<V>, val value: (T) -> V)

fun <T, V> RowAttribute<T, V>.uniqueValuesOf(ts: Iterable<T>): List<V> =
  ts.mapTo(TreeSet(comparator)) { value(it) }.toList()

/**
 * A Composable that puts a list of checkboxes in a HideablePanel and tracks their selection state.
 */
@Composable
fun <V> SetFilter(
  header: String,
  values: List<V>,
  selection: MutableMap<V, Boolean>,
  modifier: Modifier = Modifier,
) {
  if (values.size > 1) {
    HideablePanel(header, modifier.padding(6.dp)) {
      Column {
        for (item in values) {
          CheckboxRow(selection[item] ?: true, onCheckedChange = { selection[item] = it }) {
            Text(item.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
fun <V> SetFilter(values: List<V>, state: SetFilterState<*, V>, modifier: Modifier = Modifier) {
  SetFilter(state.attribute.name, values, state.selection, modifier)
}

interface RowFilter<in T> {
  fun apply(row: T): Boolean
}

/** The UI state for a single set-based attribute filter. */
class SetFilterState<T, V>(val attribute: RowAttribute<T, V>, val defaultValue: Boolean = true) :
  RowFilter<T> {
  val selection = SnapshotStateMap<V, Boolean>()

  override fun apply(row: T) = selection[attribute.value(row)] ?: defaultValue
}

/** A Dropdown that acts as a view to a SingleSelectionFilterState. */
@Composable
fun <T, V> SingleSelectionDropdown(values: List<V>, state: SingleSelectionFilterState<T, V>) {
  Column(modifier = Modifier.padding(6.dp)) {
    GroupHeader(state.attribute.name)

    Dropdown(
      modifier = Modifier.padding(2.dp),
      menuContent = {
        items(
          values,
          isSelected = { state.selection == it },
          onItemClick = { state.selection = it },
        ) {
          Text(it.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      },
    ) {
      Text(state.selection.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** A set of radio buttons that acts as a view to a SingleSelectionFilterState. */
@Composable
fun <T, V> SingleSelectionRadioButtons(values: List<V>, state: SingleSelectionFilterState<T, V>) {
  Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    GroupHeader(state.attribute.name, modifier = Modifier.padding(bottom = 6.dp))

    for (value in values) {
      RadioButtonRow(selected = state.selection == value, onClick = { state.selection = value }) {
        Text(value.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/**
 * The UI state for an attribute filter that selects one value from the values present on the rows.
 */
class SingleSelectionFilterState<T, V>(val attribute: RowAttribute<T, V>, selection: V) :
  RowFilter<T> {
  var selection by mutableStateOf(selection)

  override fun apply(row: T): Boolean = selection == attribute.value(row)
}

/** Produces a SingleSelectionFilterState with the given initial selection. */
fun <T, V> RowAttribute<T, V>.initialSingleSelectionFilterState(initialSelection: V) =
  SingleSelectionFilterState(this, initialSelection)
