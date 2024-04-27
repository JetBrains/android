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
import java.awt.Dimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GroupedListSurfaceLayoutManagerTest {

  @Test
  fun testLayoutVertically() {
    val manager =
      GroupedListSurfaceLayoutManager(0, PREVIEW_LEFT_PADDING, { 0 }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }
    val contents =
      listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
      )

    run {
      val width = 1000
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(300, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(400, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }

    run {
      val width = 1000
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(250, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(350, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }

    run {
      val width = 80
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(300, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(400, contents[4].y)
      val size = manager.getRequiredSize(contents, width, 100000, null)
      assertEquals(Dimension(100, 500), size)
    }

    run {
      val width = 80
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(250, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(350, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(100, 500), size)
    }
  }

  @Test
  fun testLayoutMultipleContentSizes() {
    val manager =
      GroupedListSurfaceLayoutManager(0, PREVIEW_LEFT_PADDING, { 0 }) { contents ->
        listOf(PositionableGroup(contents.take(3)), PositionableGroup(contents.drop(3)))
      }
    val contents =
      listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 200, 100),
        TestPositionableContent(0, 0, 300, 200),
        TestPositionableContent(0, 0, 400, 100),
        TestPositionableContent(0, 0, 200, 200),
      )

    run {
      val width = 1000
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(400, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(500, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }

    run {
      val width = 1000
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(150, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(250, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(350, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }

    run {
      val width = 80
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(0, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(100, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(200, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(400, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(500, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }

    run {
      val width = 80
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(150, contents[0].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(250, contents[1].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(350, contents[2].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(550, contents[3].y)
      assertEquals(PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(650, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(400, 700), size)
    }
  }

  @Test
  fun testPaddingAndMargin() {
    val canvasTopPadding = 10
    val framePadding = 20
    val manager =
      GroupedListSurfaceLayoutManager(
        canvasTopPadding,
        PREVIEW_LEFT_PADDING,
        { framePadding },
      ) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }
    val contents =
      listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
      )

    run {
      val width = 1000
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(170, contents[1].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(310, contents[2].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(450, contents[3].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(590, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }

    run {
      val width = 1000
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(170, contents[0].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(310, contents[1].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(590, contents[3].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(730, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }

    run {
      val width = 80
      val height = 300
      manager.layout(contents, width, height, false)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(30, contents[0].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(170, contents[1].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(310, contents[2].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(450, contents[3].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(590, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }

    run {
      val width = 80
      val height = 1000
      manager.layout(contents, width, height, false)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[0].x)
      assertEquals(170, contents[0].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[1].x)
      assertEquals(310, contents[1].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[2].x)
      assertEquals(450, contents[2].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[3].x)
      assertEquals(590, contents[3].y)
      assertEquals(framePadding + PREVIEW_LEFT_PADDING, contents[4].x)
      assertEquals(730, contents[4].y)
      val size = manager.getRequiredSize(contents, width, height, null)
      assertEquals(Dimension(140, 710), size)
    }
  }

  @Test
  fun testAdaptiveFramePadding() {
    val framePadding = 50
    val manager =
      GroupedListSurfaceLayoutManager(
        0,
        PREVIEW_LEFT_PADDING,
        { (it * framePadding).toInt() },
      ) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }
    val contents =
      listOf(
        TestPositionableContent(0, 0, 100, 100, scale = 0.5),
        TestPositionableContent(0, 0, 100, 100, scale = 1.0),
        TestPositionableContent(0, 0, 100, 100, scale = 1.0),
        TestPositionableContent(0, 0, 100, 100, scale = 2.0),
      )

    val scaledLeftPadding = { scale: Double ->
      (framePadding * scale).toInt() + PREVIEW_LEFT_PADDING
    }

    val width = 500
    val height = 500
    manager.layout(contents, width, height, false)
    assertEquals(scaledLeftPadding(0.5), contents[0].x)
    assertEquals(25, contents[0].y)
    assertEquals(scaledLeftPadding(1.0), contents[1].x)
    assertEquals(150, contents[1].y)
    assertEquals(scaledLeftPadding(1.0), contents[2].x)
    assertEquals(350, contents[2].y)
    assertEquals(scaledLeftPadding(2.0), contents[3].x)
    assertEquals(600, contents[3].y)
    val size = manager.getRequiredSize(contents, width, height, null)
    assertEquals(Dimension(400, 900), size)
  }

  @Test
  fun testScaleDoNotEffectPreferredSize() {
    val framePadding = 50
    val manager =
      GroupedListSurfaceLayoutManager(
        0,
        PREVIEW_LEFT_PADDING,
        { (it * framePadding).toInt() },
      ) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    val contentProvider: (scale: Double) -> List<PositionableContent> = {
      listOf(
        TestPositionableContent(0, 0, 100, 100, scale = it),
        TestPositionableContent(0, 0, 100, 100, scale = it),
        TestPositionableContent(0, 0, 100, 100, scale = it),
        TestPositionableContent(0, 0, 100, 100, scale = it),
      )
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
  fun testFitIntoScaleWithoutPaddings() {
    val manager =
      GroupedListSurfaceLayoutManager(0, PREVIEW_LEFT_PADDING, { 0 }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    val contents =
      listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
      )

    run {
      val scale = manager.getFitIntoScale(contents, 100, 500)
      assertEquals(1.0, scale, 0.0)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 400, 1000)
      assertEquals(2.0, scale, 0.0)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 200, 4000)
      assertEquals(2.0, scale, 0.0)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 50, 1000)
      assertEquals(0.5, scale, 0.0)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 100, 50)
      assertEquals(0.1, scale, 0.0)
    }
  }

  @Test
  fun testFitIntoScaleWithPaddings() {
    val manager =
      GroupedListSurfaceLayoutManager(0, PREVIEW_LEFT_PADDING, { 10 }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    val contents =
      listOf(
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
        TestPositionableContent(0, 0, 100, 100),
      )

    run {
      val scale = manager.getFitIntoScale(contents, 100, 1000)
      assertEquals(0.8, scale, 0.01)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 400, 1000)
      assertEquals(1.8, scale, 0.01)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 200, 1000)
      assertEquals(1.8, scale, 0.01)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 50, 1000)
      assertEquals(0.3, scale, 0.01)
    }

    run {
      val scale = manager.getFitIntoScale(contents, 100, 1000)
      assertEquals(0.8, scale, 0.01)
    }
  }

  @Test
  fun testZoomToFitValueIsIndependentOfContentScale() {
    val manager =
      GroupedListSurfaceLayoutManager(0, PREVIEW_LEFT_PADDING, { (it * 20).toInt() }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }

    val contents =
      List(4) {
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

  @Test
  fun testEmptyContent() {
    val manager =
      GroupedListSurfaceLayoutManager(0, PREVIEW_LEFT_PADDING, { 0 }) { contents ->
        listOf(PositionableGroup(contents.toList()))
      }
    val content = emptyList<PositionableContent>()
    val width = 1000
    val height = 300

    run {
      val fitScale = manager.getFitIntoScale(content, width, height)
      assertEquals(1.0, fitScale, 0.001)
      val size = manager.getRequiredSize(content, width, height, null)
      assertEquals(Dimension(0, 0), size)
    }
  }

  companion object {
    private const val PREVIEW_LEFT_PADDING = 25
  }
}
