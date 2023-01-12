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
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.intellij.util.ui.JBInsets
import junit.framework.Assert.assertEquals
import org.junit.Test
import java.awt.Dimension
import kotlin.test.assertNotEquals

class GroupedGridSurfaceLayoutManagerTest {

  // In the below comments, we use (a, b, c, ...) to represent the numbers of content in the rows.
  // For example, (9, 8, 7) means 9 items in row 1, 8 items in row 2, and 7 item in row 3.

  @Test
  fun testLayoutSingleGroup() {
    val manager = GroupedGridSurfaceLayoutManager(0, { 0 }) { contents ->
      listOf(contents.toList())
    }

    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100))

    run {
      // test all content can be put in a same row: (5)
      val width = 1000
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(200, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(300, contents[3].x)
      assertEquals(0, contents[3].y)
      assertEquals(400, contents[4].x)
      assertEquals(0, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(500, 100), size)
    }

    run {
      // test when available width is 300: (3, 2)
      val width = 300
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(200, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(100, contents[3].y)
      assertEquals(100, contents[4].x)
      assertEquals(100, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(300, 200), size)
    }

    run {
      // test when available width is 450: (4, 1)
      val width = 450
      manager.layout(contents, 450, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(200, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(300, contents[3].x)
      assertEquals(0, contents[3].y)
      assertEquals(0, contents[4].x)
      assertEquals(100, contents[4].y)
      assertEquals(Dimension(500, 100), manager.getRequiredSize(contents, 1000, 100000, null))
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(400, 200), size)
    }

    run {
      val width = 200
      // test when available width is 200: (2, 2, 1)
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(0, contents[2].x)
      assertEquals(100, contents[2].y)
      assertEquals(100, contents[3].x)
      assertEquals(100, contents[3].y)
      assertEquals(0, contents[4].x)
      assertEquals(200, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(200, 300), size)
    }

    run {
      val width = 50
      // test when available width is 50: (1, 1, 1, 1, 1)
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(0, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(0, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(300, contents[3].y)
      assertEquals(0, contents[4].x)
      assertEquals(400, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }
  }

  @Test
  fun testLayoutDifferentSizeContent() {
    val manager = GroupedGridSurfaceLayoutManager(0, { 0 }) { contents ->
      listOf(contents.toList())
    }

    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 150, 150),
                          TestPositionableContent(0, 0, 200, 200),
                          TestPositionableContent(0, 0, 300, 300))

    run {
      // test all content can be put in a same row: (5)
      val width = 1000
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(250, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(450, contents[3].x)
      assertEquals(0, contents[3].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(750, 300), size)
    }

    run {
      // test multiple rows. (2, 1, 1)
      val width = 300
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(0, contents[2].x)
      assertEquals(150, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(350, contents[3].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(300, 650), size)
    }

    run {
      // test multiple rows: (3, 1)
      val width = 450
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(250, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(200, contents[3].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(450, 500), size)
    }
  }

  @Test
  fun testLayoutMultipleGroups() {
    val manager = GroupedGridSurfaceLayoutManager(0, { 0 }) { contents ->
      listOf(contents.take(3), contents.drop(3))
    }

    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100))

    run {
      // 1 rows for each group: ((3), (2))
      val width = 1000
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(200, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(100, contents[3].y)
      assertEquals(100, contents[4].x)
      assertEquals(100, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(300, 200), size)
    }

    run {
      // test when available width is 200: ((2, 1), (2))
      val width = 300
      manager.layout(contents, width, 100000, false)
      assertEquals(0, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(0, contents[1].y)
      assertEquals(200, contents[2].x)
      assertEquals(0, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(100, contents[3].y)
      assertEquals(100, contents[4].x)
      assertEquals(100, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(300, 200), size)
    }
  }


  @Test
  fun testPaddingAndMargin() {
    val canvasTopPadding = 10
    val framePadding = 20
    // single group
    val manager1 = GroupedGridSurfaceLayoutManager(canvasTopPadding, { framePadding }) { listOf(it.toList()) }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100))
    run {
      // test single rows. (5)
      val width = 1000
      manager1.layout(contents, width, 100000, false)
      assertEquals(20, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(160, contents[1].x)
      assertEquals(30, contents[1].y)
      assertEquals(300, contents[2].x)
      assertEquals(30, contents[2].y)
      assertEquals(440, contents[3].x)
      assertEquals(30, contents[3].y)
      assertEquals(580, contents[4].x)
      assertEquals(30, contents[4].y)
      val size = manager1.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(680, 150), size)
    }

    run {
      // test 2 rows. (3, 2)
      val width = 400
      manager1.layout(contents, width, 100000, false)
      assertEquals(20, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(160, contents[1].x)
      assertEquals(30, contents[1].y)
      assertEquals(300, contents[2].x)
      assertEquals(30, contents[2].y)

      assertEquals(20, contents[3].x)
      assertEquals(170, contents[3].y)
      assertEquals(160, contents[4].x)
      assertEquals(170, contents[4].y)
      val size = manager1.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(400, 290), size)
    }

    // Multiple groups
    val manager2 = GroupedGridSurfaceLayoutManager(canvasTopPadding, { framePadding }) { listOf(contents.take(3), contents.drop(3)) }
    run {
      // test (3, 2)
      val width = 10000
      manager2.layout(contents, width, 100000, false)
      assertEquals(20, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(160, contents[1].x)
      assertEquals(30, contents[1].y)
      assertEquals(300, contents[2].x)
      assertEquals(30, contents[2].y)

      assertEquals(20, contents[3].x)
      assertEquals(170, contents[3].y)
      assertEquals(160, contents[4].x)
      assertEquals(170, contents[4].y)
      val size = manager2.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(400, 290), size)
    }
    run {
      // test (2, 1, 2)
      val width = 300
      manager2.layout(contents, width, 100000, false)
      assertEquals(20, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(160, contents[1].x)
      assertEquals(30, contents[1].y)
      assertEquals(20, contents[2].x)
      assertEquals(170, contents[2].y)

      assertEquals(20, contents[3].x)
      assertEquals(310, contents[3].y)
      assertEquals(160, contents[4].x)
      assertEquals(310, contents[4].y)
      val size = manager2.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(260, 430), size)
    }
  }

  @Test
  fun testCentralizeSinglePreview() {
    // Single visible preview case. Which the preview should be placed at the center of the window.
    val manager = GroupedGridSurfaceLayoutManager(10, { 20 }) { contents -> listOf(contents.toList()) }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100))
    run {
      manager.layout(contents, 1000, 1000, false)
      assertEquals(450, contents[0].x)
      assertEquals(450, contents[0].y)
    }
    run {
      manager.layout(contents, 60, 60, false)
      assertEquals(20, contents[0].x)
      assertEquals(20, contents[0].y)
    }
  }

  @Test
  fun testAdaptiveFramePadding() {
    val canvasTopPadding = 0
    val framePadding = 50
    val manager = GroupedGridSurfaceLayoutManager(canvasTopPadding, { (it * framePadding).toInt() }) { contents ->
      listOf(contents.toList())
    }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100, scale = 0.5),
                          TestPositionableContent(0, 0, 100, 100, scale = 1.0),
                          TestPositionableContent(0, 0, 100, 100, scale = 1.0),
                          TestPositionableContent(0, 0, 100, 100, scale = 2.0))

    run {
      // 3 rows: (2, 1, 1)
      val width = 300
      val height = 10000
      manager.layout(contents, width, height, false)
      assertEquals(25, contents[0].x)
      assertEquals(25, contents[0].y)
      assertEquals(150, contents[1].x)
      assertEquals(50, contents[1].y)
      assertEquals(50, contents[2].x)
      assertEquals(250, contents[2].y)
      assertEquals(100, contents[3].x)
      assertEquals(500, contents[3].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(300, 800), size)
    }

    run {
      // 2 rows: (3, 1)
      val width = 600
      val height = 10000
      manager.layout(contents, width, height, false)
      assertEquals(25, contents[0].x)
      assertEquals(25, contents[0].y)
      assertEquals(150, contents[1].x)
      assertEquals(50, contents[1].y)
      assertEquals(350, contents[2].x)
      assertEquals(50, contents[2].y)
      assertEquals(100, contents[3].x)
      assertEquals(300, contents[3].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(450, 600), size)
    }

    run {
      // 1 row: (4)
      val width = 10000
      val height = 10000
      manager.layout(contents, width, height, false)
      assertEquals(25, contents[0].x)
      assertEquals(25, contents[0].y)
      assertEquals(150, contents[1].x)
      assertEquals(50, contents[1].y)
      assertEquals(350, contents[2].x)
      assertEquals(50, contents[2].y)
      assertEquals(600, contents[3].x)
      assertEquals(100, contents[3].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(800, 400), size)
    }
  }


  @Test
  fun testScaleDoNotEffectPreferredSize() {
    val framePadding = 50
    val manager = GroupedGridSurfaceLayoutManager(0, { (it * framePadding).toInt() }) { contents ->
      listOf(contents.toList())
    }

    val contentProvider: (scale: Double) -> List<PositionableContent> = {
      listOf(TestPositionableContent(0, 0, 100, 100, scale = it),
             TestPositionableContent(0, 0, 100, 100, scale = it),
             TestPositionableContent(0, 0, 100, 100, scale = it),
             TestPositionableContent(0, 0, 100, 100, scale = it))
    }

    val contents1 = contentProvider(1.0)
    val contents2 = contentProvider(2.0)
    val contents3 = contentProvider(3.0)

    val width = 1000
    val height = 1000

    run {
      // When scale are different, the required size are different.
      val scaledSize1 = manager.getRequiredSize(contents1, width, height, null)
      val scaledSize2 = manager.getRequiredSize(contents2, width, height, null)
      val scaledSize3 = manager.getRequiredSize(contents3, width, height, null)
      assertNotEquals(scaledSize1, scaledSize2)
      assertNotEquals(scaledSize1, scaledSize3)
    }

    run {
      // Even the scale are different, the preferred size should be same.
      val scaledSize1 = manager.getPreferredSize(contents1, width, height, null)
      val scaledSize2 = manager.getPreferredSize(contents2, width, height, null)
      val scaledSize3 = manager.getPreferredSize(contents3, width, height, null)
      assertEquals(scaledSize1, scaledSize2)
      assertEquals(scaledSize1, scaledSize3)
    }
  }

  @Test
  fun testFitIntoScale() {
    val manager = GroupedGridSurfaceLayoutManager(0, { 0 }) { contents ->
      listOf(contents.toList())
    }

    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100))

    run {
      val scale = manager.getFitIntoScale(contents, 300, 100)
      assertEquals(0.5, scale)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 500, 100)
      assertEquals(1.0, scale)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 1000, 1000)
      assertEquals(2.0, scale)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 50, 1000)
      assertEquals(0.5, scale)
    }
  }

  @Test
  fun testZoomToFitValueIsIndependentOfContentScale() {
    val manager = GroupedGridSurfaceLayoutManager(0, { (it * 20).toInt() }) { contents ->
      listOf(contents.toList())
    }

    val contents = List(4) {
      TestPositionableContent(0, 0, 100, 100, 1.0) { scale ->
        val value = (10 * scale).toInt()
        JBInsets(value, value, value, value)
      }
    }

    val width = 1000
    val height = 1000

    val zoomToFitScale1 = manager.getFitIntoScale(contents, width, height)
    contents.forEach { it.scale = 0.5 }
    val zoomToFitScale2 = manager.getFitIntoScale(contents, width, height)
    contents.forEach { it.scale = 0.25 }
    val zoomToFitScale3 = manager.getFitIntoScale(contents, width, height)

    LayoutTestCase.assertEquals(zoomToFitScale1, zoomToFitScale2)
    LayoutTestCase.assertEquals(zoomToFitScale1, zoomToFitScale3)
  }
}
