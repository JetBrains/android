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

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class AdaptiveVerticalScrollbarAdapterTest {

  @Test
  fun testAdapterScrollOffsetAndSize() {
    val layoutInfo = mock<LazyListLayoutInfo>()
    whenever(layoutInfo.totalItemsCount).thenReturn(20)
    whenever(layoutInfo.viewportSize).thenReturn(IntSize(100, 500))

    val state = mock<LazyListState>()
    whenever(state.firstVisibleItemIndex).thenReturn(5)
    whenever(state.firstVisibleItemScrollOffset).thenReturn(20)
    whenever(state.layoutInfo).thenReturn(layoutInfo)

    val adapter = AdaptiveVerticalScrollbarAdapterImpl(state, Density(1f), initialItemCount = 10, defaultHeight = 100.dp)

    // initially tree has 10 items of 100px.
    // firstVisibleItemIndex = 5. totalHeightBefore(5) = 500.
    // firstVisibleItemScrollOffset = 20.
    // scrollOffset = 500 + 20 = 520.
    assertThat(adapter.scrollOffset).isEqualTo(520.0)

    // contentSize: tree.totalHeight (1000) + extraItems (20-10) * avgHeight (100) = 1000 + 1000 = 2000.
    assertThat(adapter.contentSize).isEqualTo(2000.0)

    assertThat(adapter.viewportSize).isEqualTo(500.0)
  }

  @Test
  fun testAdapterUpdateHeight() {
    val layoutInfo = mock<LazyListLayoutInfo>()
    whenever(layoutInfo.totalItemsCount).thenReturn(10)
    whenever(layoutInfo.viewportSize).thenReturn(IntSize(100, 500))

    val state = mock<LazyListState>()
    whenever(state.firstVisibleItemIndex).thenReturn(0)
    whenever(state.firstVisibleItemScrollOffset).thenReturn(0)
    whenever(state.layoutInfo).thenReturn(layoutInfo)

    val adapter = AdaptiveVerticalScrollbarAdapterImpl(state, Density(1f), initialItemCount = 10, defaultHeight = 100.dp)

    adapter.updateHeight(0, 150)
    // item 0: 150, others: 100. total = 1050.
    assertThat(adapter.contentSize).isEqualTo(1050.0)
  }

  @Test
  fun testAdapterDensityChange() {
    val layoutInfo = mock<LazyListLayoutInfo>()
    whenever(layoutInfo.totalItemsCount).thenReturn(10)
    whenever(layoutInfo.viewportSize).thenReturn(IntSize(100, 500))

    val state = mock<LazyListState>()
    whenever(state.firstVisibleItemIndex).thenReturn(0)
    whenever(state.firstVisibleItemScrollOffset).thenReturn(0)
    whenever(state.layoutInfo).thenReturn(layoutInfo)

    val adapter = AdaptiveVerticalScrollbarAdapterImpl(state, Density(1f), initialItemCount = 10, defaultHeight = 100.dp)

    // defaultHeight is 100.dp. With density 1f, it's 100px.
    // Total height = 1000.

    adapter.updateHeights(Density(2f))
    // Now 100.dp is 200px.
    // Total height should be 2000.
    assertThat(adapter.contentSize).isEqualTo(2000.0)
  }

  @Test
  fun testAdapterCacheInfo() {
    val layoutInfo = mock<LazyListLayoutInfo>()
    whenever(layoutInfo.totalItemsCount).thenReturn(10)
    whenever(layoutInfo.viewportSize).thenReturn(IntSize(100, 500))

    val state = mock<LazyListState>()
    whenever(state.firstVisibleItemIndex).thenReturn(0)
    whenever(state.firstVisibleItemScrollOffset).thenReturn(0)
    whenever(state.layoutInfo).thenReturn(layoutInfo)

    val adapter = AdaptiveVerticalScrollbarAdapterImpl(state, Density(1f), initialItemCount = 10, defaultHeight = 100.dp)

    val cacheInfo = adapter.cacheInfo.value
    assertThat(cacheInfo.itemCount).isEqualTo(10)
    assertThat(cacheInfo.measuredItemCount).isEqualTo(0)
    assertThat(cacheInfo.estimatedItemCount).isEqualTo(10)

    adapter.updateHeight(0, 150)
    val updatedCacheInfo = adapter.cacheInfo.value
    assertThat(updatedCacheInfo.measuredItemCount).isEqualTo(1)
    assertThat(updatedCacheInfo.estimatedItemCount).isEqualTo(9)
  }
}
