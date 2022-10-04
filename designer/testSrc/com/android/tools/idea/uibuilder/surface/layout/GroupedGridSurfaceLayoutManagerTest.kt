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
import junit.framework.Assert.assertEquals
import org.junit.Test
import java.awt.Dimension

class GroupedGridSurfaceLayoutManagerTest {

  // In the below comments, we use (a, b, c, ...) to represent the numbers of content in the rows.
  // For example, (9, 8, 7) means 9 items in row 1, 8 items in row 2, and 7 item in row 3.

  @Test
  fun testLayoutSingleGroup() {
    val manager = GroupedGridSurfaceLayoutManager(0, 0) { contents ->
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      assertEquals(Dimension(500, 100), manager.getPreferredSize(contents, 1000, 100000, null))
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }
  }

  @Test
  fun testLayoutDifferentSizeContent() {
    val manager = GroupedGridSurfaceLayoutManager(0, 0) { contents ->
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(450, 500), size)
    }
  }

  @Test
  fun testLayoutMultipleGroups() {
    val manager = GroupedGridSurfaceLayoutManager(0, 0) { contents ->
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
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
      val size = manager.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(300, 200), size)
    }
  }


  @Test
  fun testPaddingAndMargin() {
    val canvasTopPadding = 10
    val framePadding = 20
    // single group
    val manager1 = GroupedGridSurfaceLayoutManager(canvasTopPadding, framePadding) { contents ->
      listOf(contents.toList())
    }
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
      val size = manager1.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(680, 130), size)
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
      val size = manager1.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(400, 270), size)
    }

    // Multiple groups
    val manager2 = GroupedGridSurfaceLayoutManager(canvasTopPadding, framePadding) { contents ->
      // 2 groups
      listOf(contents.take(3), contents.drop(3))
    }
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
      val size = manager2.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(400, 270), size)
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
      val size = manager2.getPreferredSize(contents, width, 100000, null)
      assertEquals(Dimension(260, 410), size)
    }
  }
}
