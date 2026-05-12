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

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.annotations.concurrency.UiThread
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A custom [ScrollbarAdapter] that uses an [HeightSegmentTree] internally to keep track of the height of all items that are or have been
 * visible. The goal is to provide a scroll bar that is less "jittery" than the default [ScrollbarAdapter] implementation when using
 * [LazyColumn].
 *
 * The recommended usage is for lists that
 * * are not "infinite",
 * * contain items whose height varies and cannot be predicted cheaply,
 * * contain items whose height does not change often.
 *
 * By comparison, the default [ScrollbarAdapter] implementation uses an "avg" height of all currently visible items to predict the scrollbar
 * size and current location, which does not work well for lists that contain big items (e.g. whole viewport, for example) than are of
 * varying sizes.
 *
 * The drawback of [AdaptiveVerticalScrollbarAdapter] is memory usage: it uses a segment tree of `O(# of items that have been visible so
 * far)` entries, whereas the default [ScrollbarAdapter] does not use any additional storage (it only used the list of currently visible
 * items in [LazyListState.layoutInfo]). The [HeightSegmentTree] uses 8 bytes per item and O(log n) operations, so it can scale to hundreds
 * of thousand of items.
 *
 * See [rememberWhatsNewScrollbarAdapter] to get a default implementation from any [Composable]
 */
internal interface AdaptiveVerticalScrollbarAdapter : ScrollbarAdapter {
  /** [StateFlow] of [CacheInfo] for debugging purposes */
  val cacheInfo: StateFlow<CacheInfo>

  /** Invoke this when the [height] of the item [index] has changed */
  fun updateHeight(index: Int, height: Int)

  /** Invoke this when the estimated number of items has changed */
  fun updateTotalItemCount(totalItemCount: Int)

  data class CacheInfo(
    val itemCount: Int,
    val capacity: Int,
    val avgItemHeight: Int,
    val totalItemHeight: Int,
    val measuredItemCount: Int,
    val estimatedItemCount: Int,
  ) {
    companion object {
      val Empty =
        CacheInfo(itemCount = 0, capacity = 0, avgItemHeight = 0, totalItemHeight = 0, measuredItemCount = 0, estimatedItemCount = 0)
    }
  }
}

/**
 * Creates and remembers a [AdaptiveVerticalScrollbarAdapter] for the given [lazyListState].
 *
 * This function ensures the adapter is recreated if the screen density changes, as the adapter stores item heights in pixel units.
 */
@Composable
internal fun rememberWhatsNewScrollbarAdapter(
  lazyListState: LazyListState,
  initialItemCount: Int = 1,
  defaultHeight: Dp = 200.dp,
): AdaptiveVerticalScrollbarAdapter {
  // Note: We remember the current density so that if the density changes (i.e. moving to a different
  // monitor), a new cache is created from scratch. This is because `WhatsNewScrollbarAdapter` stores
  // height in pixel unit.
  val currentDensity = LocalDensity.current

  // We use "rememberSaveable" so that the adapter is saved (in-memory) and restore when
  // the containing composable (i.e. the editor tab) is activated and de-activated.
  val adapter =
    rememberSaveable(lazyListState, currentDensity) {
      AdaptiveVerticalScrollbarAdapterImpl(lazyListState, currentDensity, initialItemCount, defaultHeight)
    }

  // Keep the adapter in sync with the environment
  //
  // Note:
  //    The `rememberSaveable` call above ignores the input keys (e.g. lazyListState and density) when
  //    the adapter is restored from the "save" registry, so we need to ensure the existing `adapter` is
  //    up to date with the `lazyListState` and `density`.
  SideEffect {
    if (adapter.lazyListState != lazyListState) {
      adapter.lazyListState = lazyListState
    }
    if (adapter.density != currentDensity) {
      adapter.density = currentDensity
    }
  }

  // React to Density changes specifically
  // This runs once on launch and then every time currentDensity changes.
  LaunchedEffect(currentDensity) { adapter.updateHeights(currentDensity) }

  // Ensure any change to the total item count is forwarded to the adapter
  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.layoutInfo.totalItemsCount }
      .collect { totalItemCount ->
        // Ignore first layout pass, where item count is always 0
        if (totalItemCount != 0) {
          adapter.updateTotalItemCount(totalItemCount)
        }
      }
  }

  // Ensure any change to the list of visible items is forwarded to the adapter
  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
      .collect { visibleItems -> visibleItems.forEach { item -> adapter.updateHeight(item.index, item.size) } }
  }

  return adapter
}

/**
 * Implementation of [AdaptiveVerticalScrollbarAdapter] using a [HeightSegmentTree] so that most [AdaptiveVerticalScrollbarAdapter]
 * operations are either `O(1)` or `O(log N)`.
 */
@UiThread
internal class AdaptiveVerticalScrollbarAdapterImpl(
  initialState: LazyListState,
  initialDensity: Density,
  initialItemCount: Int,
  defaultHeight: Dp,
) : AdaptiveVerticalScrollbarAdapter {
  var lazyListState by mutableStateOf(initialState)
  var density by mutableStateOf(initialDensity)

  /** Track the density used in the LAST calculation */
  private var lastDensity: Density = initialDensity

  private var tree = HeightSegmentTree(initialItemCount, with(density) { defaultHeight.roundToPx() })

  private val cacheInfoState = MutableStateFlow(AdaptiveVerticalScrollbarAdapter.CacheInfo.Empty)

  override val cacheInfo: StateFlow<AdaptiveVerticalScrollbarAdapter.CacheInfo> = cacheInfoState.asStateFlow()

  private var lastTotalItemCount = 0

  init {
    publishCacheInfo()
  }

  /** Note: this is an O(n) operation if a resize is required */
  fun updateHeights(newDensity: Density) {
    if (newDensity == lastDensity) {
      return
    }

    // Update all heights to the new density
    tree.updateAll { heightPx ->
      val oldDpValue = with(lastDensity) { heightPx.toDp() }
      with(newDensity) { oldDpValue.roundToPx() }
    }

    // Update the current density and publish cache info
    lastDensity = newDensity
    publishCacheInfo()
  }

  /**
   * Time complexity:
   * * O(1) if the item height is identical to stored item height
   * * O(log n) if the item height is changing
   * * O(log n) if this is a new item, and the tree has enough room to store the new item
   * * O(n) if this is a new item, and the tree needs to grow to store the new item
   */
  override fun updateHeight(index: Int, height: Int) {
    tree.growTo(index + 1)
    tree.update(index, height)
    publishCacheInfo()
  }

  /** Note: this is an O(n) operation if a resize is required */
  override fun updateTotalItemCount(totalItemCount: Int) {
    if (totalItemCount == lastTotalItemCount) {
      return
    }
    if (totalItemCount < lastTotalItemCount) {
      tree.truncateTo(totalItemCount)
    }

    lastTotalItemCount = totalItemCount
    publishCacheInfo()
  }

  /** Note: this is an O(log n) operation */
  override val scrollOffset: Double
    get() {
      // The first visible index may be past the tree item count (if some items have never been
      // measured yet)
      val firstVisibleItemOffset =
        if (lazyListState.firstVisibleItemIndex <= tree.itemCount) {
          tree.totalHeightBefore(lazyListState.firstVisibleItemIndex)
        } else {
          tree.totalHeight + (lazyListState.firstVisibleItemIndex - tree.itemCount) * tree.avgHeight
        }
      return (firstVisibleItemOffset + lazyListState.firstVisibleItemScrollOffset).toDouble()
    }

  /** Note: this is an O(1) operation */
  override val contentSize: Double
    get() {
      // Account for items beyond the current tree size
      val extraItemsAvgHeight = maxOf(0, (lazyListState.layoutInfo.totalItemsCount - tree.itemCount)) * tree.avgHeight
      return (tree.totalHeight + extraItemsAvgHeight).toDouble()
    }

  /** Note: this is an O(1) operation */
  override val viewportSize: Double
    get() = lazyListState.layoutInfo.viewportSize.height.toDouble()

  /** Note: this is an O(log n) operation */
  override suspend fun scrollTo(scrollOffset: Double) {
    val distance = scrollOffset - this.scrollOffset

    // if we scroll less than viewport we need to use scrollBy function to avoid
    // undesirable scroll jumps (when an item size is different)
    //
    // if we scroll more than viewport we should immediately jump to this position
    // without recreating all items between the current and the new position
    if (abs(distance) <= viewportSize) {
      scrollBy(distance.toFloat())
    } else {
      snapTo(scrollOffset)
    }
  }

  suspend fun scrollBy(value: Float) {
    lazyListState.scrollBy(value)
  }

  private suspend fun snapTo(scrollOffset: Double) {
    val scrollOffsetCoerced = scrollOffset.coerceIn(0.0, maxScrollOffset).toInt()

    val (index, itemOffset) = tree.findIndexAtOffset(scrollOffsetCoerced)
    val safeIndex = index.coerceAtLeast(0).coerceAtMost(lazyListState.layoutInfo.totalItemsCount - 1)

    val safeItemOffset = itemOffset.coerceAtLeast(0)

    // We use scrollToItem to jump the LazyColumn to the exact spot
    lazyListState.scrollToItem(safeIndex, safeItemOffset)
  }

  private fun publishCacheInfo() {
    cacheInfoState.value =
      AdaptiveVerticalScrollbarAdapter.CacheInfo(
        itemCount = tree.itemCount,
        capacity = tree.capacity,
        totalItemHeight = tree.totalHeight,
        avgItemHeight = tree.avgHeight,
        measuredItemCount = tree.measuredCount,
        estimatedItemCount = tree.estimatedCount,
      )
  }
}
