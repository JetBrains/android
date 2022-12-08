/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.uibuilder.graphics.NlConstants
import org.junit.Test
import java.awt.Insets
import kotlin.test.assertEquals

class GridSurfaceLayoutManagerTest {

  @Test
  fun testGetFitIntoScale() {
    val tolerance = 0.01
    val manager = GridSurfaceLayoutManager(0, 0, 0, 0, false)

    val contents = listOf(
      TestPositionableContent(0, 0, 100, 100),
      TestPositionableContent(0, 0, 100, 100),
      TestPositionableContent(0, 0, 100, 100),
      TestPositionableContent(0, 0, 100, 100)
    )

    // Fit perfectly. (2 x 2)
    assertEquals(1.0, manager.getFitIntoScale(contents, 200, 200), tolerance)
    // Fit perfectly. (1 x 4)
    assertEquals(1.0, manager.getFitIntoScale(contents, 400, 100), tolerance)
    // Fit perfectly. (4 x 1)
    assertEquals(1.0, manager.getFitIntoScale(contents, 100, 400), tolerance)

    // Fit in 50%. (2 x 2)
    assertEquals(0.5, manager.getFitIntoScale(contents, 100, 100), tolerance)
    // Fit in 50%. (4 x 1)
    assertEquals(0.5, manager.getFitIntoScale(contents, 200, 100), tolerance)
    // Fit in 50%. (1 x 4)
    assertEquals(0.5, manager.getFitIntoScale(contents, 100, 200), tolerance)

    // Fit in 25%. (2 x 2)
    assertEquals(0.25, manager.getFitIntoScale(contents, 50, 50), tolerance)

    // Test some cases that the every content have different sizes.
    run {
      val content = listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 400, 100)
      )
      // Fit in 50%. (4, 1)
      assertEquals(0.5, manager.getFitIntoScale(content, 200, 100), tolerance)
    }
    run {
      val content = listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 200, 100),
        TestPositionableContent(0, 0, 200, 100),
        TestPositionableContent(0, 0, 100, 100),
      )
      // Fit in 50%. (2, 2)
      assertEquals(0.5, manager.getFitIntoScale(content, 300, 100), tolerance)
    }
    run {
      val content = listOf(
        TestPositionableContent(0, 0, 100, 200),
        TestPositionableContent(0, 0, 200, 100),
        TestPositionableContent(0, 0, 200, 100),
        TestPositionableContent(0, 0, 100, 200),
      )
      // Fit in 75%. (2 x 2). The total height of original size is 400.
      assertEquals(0.75, manager.getFitIntoScale(content, 300, 300), tolerance)
    }

    run {
      // Simulate the case of using Validation Tool.
      val gridSurfaceLayoutManager = GridSurfaceLayoutManager(
        NlConstants.DEFAULT_SCREEN_OFFSET_X,
        NlConstants.DEFAULT_SCREEN_OFFSET_Y,
        100,
        48,
        false
      )
      // Phone, foldable, tablet, and desktop.
      val content = listOf(
        TestPositionableContent(0, 0, 411, 891),
        TestPositionableContent(0, 0, 673, 841),
        TestPositionableContent(0, 0, 1280, 800),
        TestPositionableContent(0, 0, 1920, 1080)
      )
      assertEquals(0.15, gridSurfaceLayoutManager.getFitIntoScale(content, 400, 900), tolerance)
      assertEquals(0.25, gridSurfaceLayoutManager.getFitIntoScale(content, 900, 900), tolerance)
    }
  }
}
