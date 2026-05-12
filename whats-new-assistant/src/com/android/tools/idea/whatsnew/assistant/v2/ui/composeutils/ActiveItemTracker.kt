/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * Keeps track of an active item and allows scrolling to any item. This is a narrow abstraction of [LazyListState.firstVisibleItemIndex] and
 * [LazyListState.animateScrollToItem]
 */
internal interface ActiveItemTracker<T> {
  val currentItem: State<T>

  suspend fun scrollToItem(item: T)
}

/** An [ActiveItemTracker] from a [LazyListState] */
@Composable
internal fun <T> lazyListStateActiveItemTracker(items: List<T>, lazyListState: LazyListState): ActiveItemTracker<T> {
  return remember(items.size, lazyListState) { LazyListStateActiveItemTrackerImpl(items, lazyListState) }
}

/** An [ActiveItemTracker] from a [ScrollState] */
@Suppress("unused")
@Composable
internal fun <T> scrollStateActiveItemTracker(items: List<T>, scrollState: ScrollState): ActiveItemTracker<T> {
  return remember(items.size, scrollState) { ScrollStateActiveItemTrackerImpl(items, scrollState) }
}

/**
 * A [Column] composable that displays a list of [items] using the [item] composable and tracks the currently visible item using an
 * [ActiveItemTracker].
 */
@Suppress("unused")
@Composable
internal fun <T> ColumnWithActiveItemTracking(
  modifier: Modifier = Modifier,
  items: List<T>,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  tracker: ActiveItemTracker<T>,
  item: @Composable ColumnScope.(index: Int, item: T, modifier: Modifier) -> Unit,
) {
  tracker as ScrollStateActiveItemTrackerImpl
  var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  Column(modifier.onGloballyPositioned { containerCoordinates = it }) {
    items.forEachIndexed { index, t ->
      val modifier =
        Modifier.onGloballyPositioned { coordinates ->
          containerCoordinates?.localPositionOf(coordinates)?.also { rowOffset -> tracker.onItemLayout(t, rowOffset, coordinates.size) }
        }
      item(index, t, modifier)
    }
  }
}

private class LazyListStateActiveItemTrackerImpl<T>(private val items: List<T>, private val lazyListState: LazyListState) :
  ActiveItemTracker<T> {
  override val currentItem = derivedStateOf { items[lazyListState.firstVisibleItemIndex] }

  override suspend fun scrollToItem(item: T) {
    val index = items.indexOf(item)
    if (index >= 0) {
      lazyListState.animateScrollToItem(index)
    }
  }
}

private class ScrollStateActiveItemTrackerImpl<T>(private val items: List<T>, private val scrollState: ScrollState) : ActiveItemTracker<T> {
  private val heights = mutableStateMapOf<T, Pair<Offset, IntSize>>()

  override val currentItem: State<T> = derivedStateOf {
    val currentOffset = scrollState.value
    val entry =
      heights.asSequence().firstOrNull { entry ->
        val entryOffset = entry.value.first.y.toInt()
        entryOffset <= currentOffset && currentOffset < entryOffset + entry.value.second.height
      }
    entry?.key ?: items[0]
  }

  fun onItemLayout(item: T, itemOffset: Offset, itemSize: IntSize) {
    heights[item] = Pair(itemOffset, itemSize)
  }

  override suspend fun scrollToItem(item: T) {
    heights[item]?.also { scrollState.animateScrollTo(it.first.y.toInt()) }
  }
}
