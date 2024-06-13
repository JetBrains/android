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
package com.android.tools.idea.uibuilder.surface.layout

import com.android.tools.idea.common.surface.layout.TestPositionableContent
import com.android.tools.idea.flags.StudioFlags.PREVIEW_DYNAMIC_ZOOM_TO_FIT
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DynamicZoomToFitTest {

  private val tolerance = 0.01

  @Before
  fun setUp() {
    PREVIEW_DYNAMIC_ZOOM_TO_FIT.override(true)
  }

  @After
  fun tearDown() {
    PREVIEW_DYNAMIC_ZOOM_TO_FIT.clearOverride()
  }

  @Test
  fun testFitIntoScaleListLayoutEmptyList() {
    val manager =
      GroupedListSurfaceLayoutManager(GroupPadding(0, PREVIEW_LEFT_PADDING) { 0 }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    run {
      val scale = manager.getFitIntoScale(emptyList(), 100, 500)
      assertEquals(1.0, scale, 0.0)
    }
  }

  @Test
  fun testFitIntoScaleListLayout() {
    val manager =
      GroupedListSurfaceLayoutManager(GroupPadding(0, PREVIEW_LEFT_PADDING) { 0 }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    val contents = List(15) { TestPositionableContent(0, 0, 100, 100) }

    run {
      val scale = manager.getFitIntoScale(contents.take(5), 100, 500)
      assertEquals(1.0, scale, 0.0)
    }

    run {
      val scale = manager.getFitIntoScale(contents.take(6), 100, 500)
      assertEquals(0.83, scale, tolerance)
    }

    // Scale stop changing after some number of previews
    run {
      val scale = manager.getFitIntoScale(contents.take(7), 100, 500)
      assertEquals(0.71, scale, tolerance)
    }

    run {
      val scale = manager.getFitIntoScale(contents.take(15), 100, 500)
      assertEquals(0.71, scale, tolerance)
    }
  }

  @Test
  fun testFitIntoScaleGridLayoutEmptyList() {
    val manager =
      GroupedGridSurfaceLayoutManager(EMPTY_PADDING) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }
    run {
      val scale = manager.getFitIntoScale(emptyList(), 50, 50)
      assertEquals(1.0, scale, tolerance)
    }
  }

  @Test
  fun testFitIntoScaleGridLayout() {
    val manager =
      GroupedGridSurfaceLayoutManager(EMPTY_PADDING) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    val contents = List(15) { TestPositionableContent(0, 0, 80, 80) }

    run {
      val scale = manager.getFitIntoScale(contents.take(1), 300, 300)
      assertEquals(3.75, scale, tolerance)
    }

    run {
      val scale = manager.getFitIntoScale(contents.take(3), 300, 300)
      assertEquals(1.88, scale, tolerance)
    }

    run {
      val scale = manager.getFitIntoScale(contents.take(4), 300, 300)
      assertEquals(1.87, scale, tolerance)
    }

    // Scale stop changing after some number of previews
    run {
      val scale = manager.getFitIntoScale(contents.take(7), 300, 300)
      assertEquals(1.25, scale, tolerance)
    }

    run {
      val scale = manager.getFitIntoScale(contents.take(9), 300, 300)
      assertEquals(1.25, scale, tolerance)
    }

    run {
      val scale = manager.getFitIntoScale(contents.take(15), 300, 300)
      assertEquals(1.25, scale, tolerance)
    }
  }
}
