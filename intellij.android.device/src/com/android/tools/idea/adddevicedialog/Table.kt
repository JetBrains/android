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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.WhenScrolling
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.jewel.ui.util.thenIf
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

data class TableColumn<in T>(
  val name: String,
  val width: TableColumnWidth,
  val comparator: Comparator<in T>? = null,
  val reverseComparator: Comparator<in T>? = comparator?.reversed(),
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

/**
 * A sortable column that displays the attribute as text via toString() and sorts via the
 * attribute's natural order.
 */
fun <T, V : Comparable<V>> DefaultSortableTableColumn(
  name: String,
  width: TableColumnWidth = TableColumnWidth.Weighted(1f),
  attribute: (T) -> V,
  comparator: Comparator<T>? = compareBy(attribute),
  overflow: TextOverflow = TextOverflow.Ellipsis,
  maxLines: Int = 1,
) =
  TableColumn(name, width, comparator) {
    Text(attribute(it).toString(), overflow = overflow, maxLines = maxLines)
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
    SortOrder.ASCENDING -> Icon(AllIconsKeys.General.ArrowUp, "Sorted ascending")
    SortOrder.DESCENDING -> Icon(AllIconsKeys.General.ArrowDown, "Sorted descending")
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
    get() =
      when (sortOrder) {
        SortOrder.ASCENDING -> sortColumn?.comparator
        SortOrder.DESCENDING -> sortColumn?.reverseComparator
      }
}

@Composable
internal fun <T> TableHeader(
  sortColumn: TableColumn<T>?,
  sortOrder: SortOrder,
  onClick: (TableColumn<T>) -> Unit,
  columns: List<TableColumn<T>>,
  modifier: Modifier = Modifier,
) {
  Row(modifier.fillMaxWidth().padding(horizontal = ROW_PADDING)) {
    columns.forEach {
      val widthModifier = with(it.width) { widthModifier() }
      var isFocused by remember { mutableStateOf(false) }
      Row(
        widthModifier
          .semantics(mergeDescendants = true) { heading() }
          .thenIf(isFocused) {
            background(JewelTheme.colorPalette.gray(if (JewelTheme.isDark) 3 else 12))
          }
          .onFocusChanged { isFocused = it.isFocused }
          .thenIf(it.comparator != null) {
            clickable(interactionSource = null, indication = null) { onClick(it) }
          }
          .padding(horizontal = CELL_SPACING / 2, vertical = CELL_SPACING)
      ) {
        Text(it.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (it == sortColumn) {
          sortOrder.Icon()
        }
      }
    }

    Spacer(Modifier.width(scrollbarHeaderPadding()))
  }
}

/**
 * Returns the padding needed to keep the header aligned with the content area of a
 * ScrollableContainer below it.
 */
@Composable
private fun scrollbarHeaderPadding(style: ScrollbarStyle = JewelTheme.scrollbarStyle): Dp =
  // This is org.jetbrains.jewel.ui.component.scrollbarContentSafePadding() with two values swapped.
  when {
    hostOs != OS.MacOS -> style.scrollbarVisibility.trackThicknessExpanded
    style.scrollbarVisibility is AlwaysVisible -> style.scrollbarVisibility.trackThicknessExpanded
    style.scrollbarVisibility is WhenScrolling -> 0.dp
    else -> error("Unsupported visibility: ${style.scrollbarVisibility}")
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
  var isHovered by remember { mutableStateOf(false) }
  var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  Row(
    modifier
      .focusProperties { canFocus = false }
      .thenIf(isHovered) {
        background(
          retrieveColorOrUnspecified("Table.hoverBackground").takeOrElse { Color.LightGray }
        )
      }
      .thenIf(selected) {
        background(
          retrieveColorOrUnspecified("Table.selectionBackground").takeOrElse { Color.Cyan }
        )
      }
      .onHover { isHovered = it }
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
      .selectable(
        selected,
        interactionSource = null,
        indication = null,
        onClick = { onClick(value) },
      )
      .padding(ROW_PADDING)
      .fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
    verticalAlignment = Alignment.CenterVertically,
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
  lazyListState: LazyListState = rememberLazyListState(),
  tableSortState: TableSortState<T> = remember { TableSortState() },
  tableSelectionState: TableSelectionState<T> = remember { TableSelectionState<T>() },
  onRowClick: (T) -> Unit = { tableSelectionState.selection = it },
  onRowSecondaryClick: (T, Offset) -> Unit = { _, _ -> },
) {
  val sortedRows = tableSortState.comparator?.let { rows.sortedWith(it) } ?: rows
  val tableFocusRequester = remember { FocusRequester() }
  val coroutineScope = rememberCoroutineScope()

  // Keep the selection visible in the list in response to changes in order of rows.
  LaunchedEffect(sortedRows) {
    val index = sortedRows.indexOf(tableSelectionState.selection)
    if (index >= 0) {
      lazyListState.scrollToItem(index)
    }
  }

  Column(
    modifier.padding(ROW_PADDING).onKeyEvent { event ->
      if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
      val index = sortedRows.indexOf(tableSelectionState.selection)
      val newIndex =
        (index +
            when (event.key) {
              Key.DirectionDown -> 1
              Key.DirectionUp -> -1
              else -> return@onKeyEvent false
            })
          .coerceIn(sortedRows.indices)
      if (newIndex != index) {
        val layoutInfo = lazyListState.layoutInfo
        val newIndexInfo = layoutInfo.visibleItemsInfo.find { it.index == newIndex }
        val shouldScroll =
          newIndexInfo == null ||
            newIndexInfo.offset < layoutInfo.viewportStartOffset ||
            newIndexInfo.offset + newIndexInfo.size > layoutInfo.viewportEndOffset
        if (shouldScroll) {
          coroutineScope.launch {
            lazyListState.scrollToItem(newIndex)
            tableSelectionState.selection = sortedRows[newIndex]
          }
        } else {
          tableSelectionState.selection = sortedRows[newIndex]
        }
      }
      true
    }
  ) {
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
    )
    Divider(Orientation.Horizontal)
    VerticallyScrollableContainer(scrollState = lazyListState, Modifier) {
      LazyColumn(
        state = lazyListState,
        modifier =
          Modifier.onFocusChanged { focusState ->
              if (focusState.hasFocus && tableSelectionState.selection == null) {
                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                  tableSelectionState.selection = sortedRows[it.index]
                }
              }
            }
            .focusRequester(tableFocusRequester)
            .focusable(),
      ) {
        items(sortedRows.size, { index -> rowId(sortedRows[index]) }) { index ->
          TableRow(
            sortedRows[index],
            selected = sortedRows[index] == tableSelectionState.selection,
            { row ->
              tableFocusRequester.requestFocus()
              onRowClick(row)
            },
            onRowSecondaryClick,
            columns,
          )
        }
      }
    }
  }
}

private val CELL_SPACING = 4.dp
private val ROW_PADDING = 4.dp
