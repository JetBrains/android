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
import kotlin.test.assertNotEquals

class GroupedListSurfaceLayoutManagerTest {

  @Test
  fun testLayoutVertically() {
    val manager = GroupedListSurfaceLayoutManager(0,  { 0 }) { contents ->
      listOf(contents.toList())
    }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100))

    run {
      val width = 1000
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(450, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(450, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(450, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(450, contents[3].x)
      assertEquals(300, contents[3].y)
      assertEquals(450, contents[4].x)
      assertEquals(400, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }

    run {
      val width = 1000
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(450, contents[0].x)
      assertEquals(250, contents[0].y)
      assertEquals(450, contents[1].x)
      assertEquals(350, contents[1].y)
      assertEquals(450, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(450, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(450, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }

    run {
      val width = 80
      val height = 300
      manager.layout(contents, width, height, false)
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

    run {
      val width = 80
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(0, contents[0].x)
      assertEquals(250, contents[0].y)
      assertEquals(0, contents[1].x)
      assertEquals(350, contents[1].y)
      assertEquals(0, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(0, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(100, 500), size)
    }
  }

  @Test
  fun testLayoutMultipleContentSizes() {
    val manager = GroupedListSurfaceLayoutManager(0, { 0 }) { contents ->
      listOf(contents.take(3), contents.drop(3))
    }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 200, 100),
                          TestPositionableContent(0, 0, 300, 200),
                          TestPositionableContent(0, 0, 400, 100),
                          TestPositionableContent(0, 0, 200, 200))

    run {
      val width = 1000
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(450, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(400, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(350, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(300, contents[3].x)
      assertEquals(400, contents[3].y)
      assertEquals(400, contents[4].x)
      assertEquals(500, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }

    run {
      val width = 1000
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(450, contents[0].x)
      assertEquals(150, contents[0].y)
      assertEquals(400, contents[1].x)
      assertEquals(250, contents[1].y)
      assertEquals(350, contents[2].x)
      assertEquals(350, contents[2].y)
      assertEquals(300, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(400, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }

    run {
      val width = 80
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(150, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(50, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(400, contents[3].y)
      assertEquals(100, contents[4].x)
      assertEquals(500, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }

    run {
      val width = 80
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(150, contents[0].x)
      assertEquals(150, contents[0].y)
      assertEquals(100, contents[1].x)
      assertEquals(250, contents[1].y)
      assertEquals(50, contents[2].x)
      assertEquals(350, contents[2].y)
      assertEquals(0, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(100, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }
  }


  @Test
  fun testPaddingAndMargin() {
    val canvasTopPadding = 10
    val framePadding = 20
    val manager = GroupedListSurfaceLayoutManager(canvasTopPadding, { framePadding }) { contents ->
      listOf(contents.toList())
    }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100),
                          TestPositionableContent(0, 0, 100, 100))

    run {
      val width = 1000
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(450, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(450, contents[1].x)
      assertEquals(170, contents[1].y)
      assertEquals(450, contents[2].x)
      assertEquals(310, contents[2].y)
      assertEquals(450, contents[3].x)
      assertEquals(450, contents[3].y)
      assertEquals(450, contents[4].x)
      assertEquals(590, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }

    run {
      val width = 1000
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(450, contents[0].x)
      assertEquals(170, contents[0].y)
      assertEquals(450, contents[1].x)
      assertEquals(310, contents[1].y)
      assertEquals(450, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(450, contents[3].x)
      assertEquals(590, contents[3].y)
      assertEquals(450, contents[4].x)
      assertEquals(730, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }

    run {
      val width = 80
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(20, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(20, contents[1].x)
      assertEquals(170, contents[1].y)
      assertEquals(20, contents[2].x)
      assertEquals(310, contents[2].y)
      assertEquals(20, contents[3].x)
      assertEquals(450, contents[3].y)
      assertEquals(20, contents[4].x)
      assertEquals(590, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }

    run {
      val width = 80
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(20, contents[0].x)
      assertEquals(170, contents[0].y)
      assertEquals(20, contents[1].x)
      assertEquals(310, contents[1].y)
      assertEquals(20, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(20, contents[3].x)
      assertEquals(590, contents[3].y)
      assertEquals(20, contents[4].x)
      assertEquals(730, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }
  }

  @Test
  fun testAdaptiveFramePadding() {
    val framePadding = 50
    val manager = GroupedListSurfaceLayoutManager(0, { (it * framePadding).toInt() }) { contents ->
      listOf(contents.toList())
    }
    val contents = listOf(TestPositionableContent(0, 0, 100, 100, scale = 0.5),
                          TestPositionableContent(0, 0, 100, 100, scale = 1.0),
                          TestPositionableContent(0, 0, 100, 100, scale = 1.0),
                          TestPositionableContent(0, 0, 100, 100, scale = 2.0))

    val width = 500
    val height = 500
    manager.layout(contents, width, height, false)
    assertEquals(225, contents[0].x)
    assertEquals(25, contents[0].y)
    assertEquals(200, contents[1].x)
    assertEquals(150, contents[1].y)
    assertEquals(200, contents[2].x)
    assertEquals(350, contents[2].y)
    assertEquals(150, contents[3].x)
    assertEquals(600, contents[3].y)
    val size = manager.getRequiredSize(contents, width, height, null)
    assertEquals(Dimension(400, 900), size)
  }

  @Test
  fun testScaleDoNotEffectPreferredSize() {
    val framePadding = 50
    val manager = GroupedListSurfaceLayoutManager(0, { (it * framePadding).toInt() }) { contents ->
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
}
