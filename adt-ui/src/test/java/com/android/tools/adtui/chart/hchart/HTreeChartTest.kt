/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.chart.hchart

import com.android.tools.adtui.model.DefaultHNode
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.swing.FakeUi
import org.junit.Before
import org.junit.Test

import java.awt.*

import com.google.common.truth.Truth.assertThat

class HTreeChartTest {
  private lateinit var myUi: FakeUi
  private lateinit var myChart: HTreeChart<DefaultHNode<String>>
  private lateinit var myRange: Range
  // We make the chart's dimension to be shorter than the tree's height, so we can test dragging
  // towards north and south.
  private val myContentHeight = 75
  // The total heigh is the height of the content plus the height of the padding.
  private val myTotalHeight = myContentHeight + 15
  private val myViewHeight = 50
  // Y axis' initial position, which is the north/south boundary of a top-down/bottom-up chart.
  private val myInitialYPosition = 0

  @Before
  fun setUp() {
    setUp(HTreeChart.Orientation.BOTTOM_UP)
  }

  private fun setUp(orientation: HTreeChart.Orientation) {
    myRange = Range(0.0, 100.0)
    myChart = HTreeChart(Range(0.0, 100.0), myRange, orientation)
    myChart.size = Dimension(100, myViewHeight)
    myUi = FakeUi(myChart)
    myChart.yRange.set(10.0, 10.0)
    // Set a root pointing to a tree with more than one nodes, to perform some meaningful drags.
    myChart.setHTree(HNodeTree(0,5,2))
    assertThat(myChart.maximumHeight).isEqualTo(myTotalHeight)
  }

  /**
   * Returns the root of a complete tree of DefaultHNode<String>. The root is at the given depth,
   * and the subtree has the given height and branch factor.
   */
  private fun HNodeTree(depth: Int, height: Int, branch: Int): DefaultHNode<String> {
    val subroot = DefaultHNode("")
    subroot.depth = depth
    if (height > 1) {
      for (i in 1..branch) {
        subroot.addChild(HNodeTree(depth + 1, height - 1, branch))
      }
    }
    return subroot
  }

  @Test
  fun testMouseDragToEast() {
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 60.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(10, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(7.5)
    assertThat(myRange.max).isWithin(EPSILON).of(57.5)
  }

  @Test
  fun testMouseOverDragToEast() {
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 60.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(90, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(50.0)
  }

  @Test
  fun testMouseDragToWest() {
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 60.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(3, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(11.0)
    assertThat(myRange.max).isWithin(EPSILON).of(61.0)
  }

  @Test
  fun testMouseOverDragToWest() {
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 60.0)
    myUi.mouse.press(95, 5)
    myUi.mouse.dragTo(5, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(50.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
  }

  @Test
  fun testMouseDragToSouth() {
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)

    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 7)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(12.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(12.0)
  }

  @Test
  fun testMouseOverDragToSouth() {
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)

    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 45)

    assertThat(myChart.yRange.min.toInt()).isEqualTo(myTotalHeight - myViewHeight)
    assertThat(myChart.yRange.max.toInt()).isEqualTo(myTotalHeight - myViewHeight)
  }

  @Test
  fun testMouseDragToNorth() {
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)

    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 1)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(6.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(6.0)
  }

  @Test
  fun testMouseOverDragToNorth() {
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)

    myUi.mouse.press(5, 45)
    myUi.mouse.dragTo(5, 5)

    assertThat(myChart.yRange.min.toInt()).isEqualTo(myInitialYPosition)
    assertThat(myChart.yRange.max.toInt()).isEqualTo(myInitialYPosition)
  }

  @Test
  fun testMouseDragSouthWhenChartTallerThanTree() {
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)

    // Resize the chart so the view's height is larger than the content's.
    // The drag should be no-op.
    myChart.size = Dimension(100, myTotalHeight + 20)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 7)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)
  }

  @Test
  fun testMouseDragEastSouth() {
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)

    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 60.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(7, 10)

    assertThat(myRange.min).isWithin(EPSILON).of(9.0)
    assertThat(myRange.max).isWithin(EPSILON).of(59.0)
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(15.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(15.0)
  }

  @Test
  fun testTopDownChartDragToSouth() {
    setUp(HTreeChart.Orientation.TOP_DOWN)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 10)
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(5.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(5.0)
  }

  @Test
  fun testTopDownChartOverDragToSouth() {
    setUp(HTreeChart.Orientation.TOP_DOWN)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 40)
    assertThat(myChart.yRange.min.toInt()).isEqualTo(myInitialYPosition)
    assertThat(myChart.yRange.max.toInt()).isEqualTo(myInitialYPosition)
  }

  @Test
  fun testTopDownChartDragToNorth() {
    setUp(HTreeChart.Orientation.TOP_DOWN)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)
    myUi.mouse.press(5, 5)
    myUi.mouse.dragTo(5, 0)
    assertThat(myChart.yRange.min).isWithin(EPSILON).of(15.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(15.0)
  }

  @Test
  fun testTopDownChartOverDragToNorth() {
    setUp(HTreeChart.Orientation.TOP_DOWN)

    assertThat(myChart.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart.yRange.max).isWithin(EPSILON).of(10.0)
    myUi.mouse.press(5, 45)
    myUi.mouse.dragTo(5, 0)
    assertThat(myChart.yRange.min.toInt()).isEqualTo(myTotalHeight - myViewHeight)
    assertThat(myChart.yRange.max.toInt()).isEqualTo(myTotalHeight - myViewHeight)
  }

  @Test
  fun testChartMouseWheelZoomIn() {
    setUp(HTreeChart.Orientation.TOP_DOWN)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    myUi.mouse.wheel(40, 20, -1)
    assertThat(myRange.min).isWithin(EPSILON).of(2.0)
    assertThat(myRange.max).isWithin(EPSILON).of(97.0)
  }

  @Test
  fun testChartMouseWheelZoomOut() {
    setUp(HTreeChart.Orientation.TOP_DOWN)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 60.0)
    myUi.mouse.wheel(40, 20, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(5.0)
    assertThat(myRange.max).isWithin(EPSILON).of(67.5)
  }

  @Test
  fun testChartMouseWheelOverZoomOutLeft() {
    setUp(HTreeChart.Orientation.TOP_DOWN)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(1.0, 51.0)
    myUi.mouse.wheel(80, 20, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(53.5)
  }

  @Test
  fun testChartMouseWheelOverZoomOutRight() {
    setUp(HTreeChart.Orientation.TOP_DOWN)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(49.0, 99.0)
    myUi.mouse.wheel(20, 20, 5)
    assertThat(myRange.min).isWithin(EPSILON).of(46.5)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
  }

  @Test
  fun testChartMouseWheelOverZoomOutBothSides() {
    setUp(HTreeChart.Orientation.TOP_DOWN)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
    // Zoom in by 2X. Only zoomed-in chart supports horizontal drag.
    myRange.set(10.0, 90.0)
    myUi.mouse.wheel(25, 20, 15)
    assertThat(myRange.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange.max).isWithin(EPSILON).of(100.0)
  }

  companion object {
    private val EPSILON = 1e-3
  }
}
