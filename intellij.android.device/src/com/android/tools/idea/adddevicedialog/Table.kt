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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.util.thenIf

data class TableColumn<in T>(
  val name: String,
  val width: TableColumnWidth,
  val comparator: Comparator<in T>? = null,
  val rowContent: @Composable (T) -> Unit,
)

@Suppress("ModifierFactoryExtensionFunction")
sealed interface TableColumnWidth {
  fun RowScope.widthModifier(): Modifier

  class Fixed(val width: Dp) : TableColumnWidth {
    override fun RowScope.widthModifier(): Modifier = Modifier.width(width)
  }

  class Weighted(val weight: Float) : TableColumnWidth {
    override fun RowScope.widthModifier(): Modifier = Modifier.weight(weight, fill = true)
  }
}

fun <T> TableTextColumn(
  name: String,
  width: TableColumnWidth = TableColumnWidth.Weighted(1f),
  attribute: (T) -> String,
  comparator: Comparator<T>? = compareBy(attribute),
  overflow: TextOverflow = TextOverflow.Ellipsis,
  maxLines: Int = 1,
) =
  TableColumn(name, width, comparator) {
    Text(attribute(it), overflow = overflow, maxLines = maxLines)
  }

enum class SortOrder {
  ASCENDING,
  DESCENDING;

  val opposite: SortOrder
    get() =
      when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
      }
}

@Composable
internal fun SortOrder.Icon() =
  when (this) {
    // In Swing, we would do `UIManager.get("Table.ascendingSortIcon", null) as Icon`; instead use
    // IJ platform icons
    SortOrder.ASCENDING -> Icon(AllIconsKeys.General.ArrowUp, null)
    SortOrder.DESCENDING -> Icon(AllIconsKeys.General.ArrowDown, null)
  }

@Stable
class TableSelectionState<T>(selectedValue: T? = null) {
  var selection by mutableStateOf(selectedValue)
}

@Stable
class TableSortState<T> {
  var sortColumn: TableColumn<T>? by mutableStateOf(null)
  var sortOrder: SortOrder by mutableStateOf(SortOrder.ASCENDING)

  val comparator: Comparator<in T>?
    get() = sortColumn?.comparator?.reverseIf(sortOrder == SortOrder.DESCENDING)

  // Without this auxiliary method, typechecking fails
  private fun <T> Comparator<T>.reverseIf(reverse: Boolean) = if (reverse) reversed() else this
}

@Composable
internal fun <T> TableHeader(
  sortColumn: TableColumn<T>?,
  sortOrder: SortOrder,
  onClick: (TableColumn<T>) -> Unit,
  columns: List<TableColumn<T>>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.fillMaxWidth().padding(ROW_PADDING),
    horizontalArrangement = Arrangement.spacedBy(CELL_SPACING / 2),
  ) {
    columns.forEach {
      val widthModifier = with(it.width) { widthModifier() }
      var isFocused by remember { mutableStateOf(false) }
      Row(
        widthModifier
          .thenIf(isFocused) { focusBorder() }
          .padding(CELL_SPACING / 2)
          .onFocusChanged { isFocused = it.isFocused }
          .thenIf(it.comparator != null) { clickable { onClick(it) } }
      ) {
        Text(it.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (it == sortColumn) {
          sortOrder.Icon()
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> TableRow(
  value: T,
  selected: Boolean,
  onClick: (T) -> Unit = {},
  onSecondaryClick: (T, Offset) -> Unit = { _, _ -> },
  columns: List<TableColumn<T>>,
  modifier: Modifier = Modifier,
) {
  var isFocused by remember { mutableStateOf(false) }
  var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  Row(
    modifier
      // Divide the padding before and after the border
      .padding(ROW_PADDING / 2)
      .thenIf(selected) {
        background(
          retrieveColorOrUnspecified("Table.selectionBackground").takeOrElse { Color.Cyan }
        )
      }
      .thenIf(isFocused) { focusBorder() }
      .pointerInput(value) {
        detectTapGestures(
          PointerMatcher.mouse(PointerButton.Secondary),
          onPress = { offset ->
            val layoutCoordinates = layoutCoordinates ?: return@detectTapGestures
            onSecondaryClick(value, layoutCoordinates.localToRoot(offset))
          },
        )
      }
      .onGloballyPositioned { layoutCoordinates = it }
      .onFocusChanged { isFocused = it.isFocused }
      .selectable(selected, onClick = { onClick(value) })
      .padding(ROW_PADDING / 2)
      .fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
  ) {
    val contentColor =
      if (selected) retrieveColorOrUnspecified("Table.selectionForeground")
      else LocalContentColor.current
    CompositionLocalProvider(LocalContentColor provides contentColor) {
      columns.forEach { Box(with(it.width) { widthModifier() }) { it.rowContent(value) } }
    }
  }
}

@Composable
fun <T> Table(
  columns: List<TableColumn<T>>,
  rows: List<T>,
  rowId: (T) -> Any,
  modifier: Modifier = Modifier,
  tableSortState: TableSortState<T> = remember { TableSortState() },
  tableSelectionState: TableSelectionState<T> = remember { TableSelectionState<T>() },
  onRowClick: (T) -> Unit = { tableSelectionState.selection = it },
  onRowSecondaryClick: (T, Offset) -> Unit = { _, _ -> },
) {
  Column(modifier.padding(ROW_PADDING)) {
    val sortedRows = tableSortState.comparator?.let { rows.sortedWith(it) } ?: rows
    val focusRequesters = remember(sortedRows) { Array(sortedRows.size) { FocusRequester() } }

    TableHeader(
      tableSortState.sortColumn,
      tableSortState.sortOrder,
      onClick = { column ->
        if (column.comparator != null) {
          tableSortState.sortOrder =
            if (tableSortState.sortColumn == column) tableSortState.sortOrder.opposite
            else SortOrder.ASCENDING
          tableSortState.sortColumn = column
        }
      },
      columns,
      Modifier.onKeyEvent { event ->
        when {
          event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
            tableSelectionState.selection = sortedRows[0]
            focusRequesters[0].requestFocus()
            true
          }
          else -> false
        }
      },
    )
    Divider(Orientation.Horizontal)
    val lazyListState = rememberLazyListState()
    VerticallyScrollableContainer(scrollState = lazyListState) {
      LazyColumn(
        state = lazyListState,
        modifier =
          Modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val index = sortedRows.indexOf(tableSelectionState.selection)
            if (index < 0) return@onKeyEvent false
            val newIndex =
              (index +
                  when (event.key) {
                    Key.DirectionDown -> 1
                    Key.DirectionUp -> -1
                    else -> return@onKeyEvent false
                  })
                .coerceIn(sortedRows.indices)
            if (newIndex != index) {
              tableSelectionState.selection = sortedRows[newIndex]
              focusRequesters[newIndex].requestFocus()
            }
            true
          },
      ) {
        items(sortedRows.size, { index -> rowId(sortedRows[index]) }) { index ->
          TableRow(
            sortedRows[index],
            selected = sortedRows[index] == tableSelectionState.selection,
            onRowClick,
            onRowSecondaryClick,
            columns,
            Modifier.focusRequester(focusRequesters[index]),
          )
        }
      }
    }
  }
}

@Composable
private fun Modifier.focusBorder() =
  border(
    Stroke.Alignment.Center,
    shape = RoundedCornerShape(4.dp),
    color = JewelTheme.globalColors.outlines.focused,
    width = JewelTheme.globalMetrics.outlineWidth,
  )

private val CELL_SPACING = 4.dp
private val ROW_PADDING = 4.dp
