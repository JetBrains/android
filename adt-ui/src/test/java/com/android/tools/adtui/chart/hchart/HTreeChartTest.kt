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

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.swing.FakeUi
import org.junit.Before
import org.junit.Test

import java.awt.*

import com.google.common.truth.Truth.assertThat

class HTreeChartTest {

  private var myUi: FakeUi? = null
  private var myChart: HTreeChart<String>? = null
  private var myRange: Range? = null

  @Before
  fun setUp() {
    setUp(HTreeChart.Orientation.BOTTOM_UP)
  }

  private fun setUp(orientation: HTreeChart.Orientation) {
    myRange = Range(0.0, 100.0)
    myChart = HTreeChart<String>(myRange, orientation)
    myChart!!.size = Dimension(100, 100)
    myUi = FakeUi(myChart!!)
    myChart!!.yRange.set(10.0, 10.0)
  }

  @Test
  fun testMouseDragToEast() {
    assertThat(myRange!!.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange!!.max).isWithin(EPSILON).of(100.0)

    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(10, 5)

    assertThat(myRange!!.min).isWithin(EPSILON).of(-5.0)
    assertThat(myRange!!.max).isWithin(EPSILON).of(95.0)
  }

  @Test
  fun testMouseDragToWest() {
    assertThat(myRange!!.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange!!.max).isWithin(EPSILON).of(100.0)
    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(3, 5)
    assertThat(myRange!!.min).isWithin(EPSILON).of(2.0)
    assertThat(myRange!!.max).isWithin(EPSILON).of(102.0)
  }

  @Test
  fun testMouseDragToSouth() {
    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(10.0)

    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(5, 7)

    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(12.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(12.0)
  }

  @Test
  fun testMouseDragToNorth() {
    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(10.0)

    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(5, 1)

    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(6.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(6.0)
  }

  @Test
  fun testMouseDragEastSouth() {
    assertThat(myRange!!.min).isWithin(EPSILON).of(0.0)
    assertThat(myRange!!.max).isWithin(EPSILON).of(100.0)
    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(10.0)

    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(7, 10)

    assertThat(myRange!!.min).isWithin(EPSILON).of(-2.0)
    assertThat(myRange!!.max).isWithin(EPSILON).of(98.0)
    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(15.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(15.0)
  }

  @Test
  fun testTopDownChartDragToEast() {
    setUp(HTreeChart.Orientation.TOP_DOWN)

    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(10.0)
    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(5, 10)
    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(5.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(5.0)
  }

  @Test
  fun testTopDownChartDragToWest() {
    setUp(HTreeChart.Orientation.TOP_DOWN)

    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(10.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(10.0)
    myUi!!.mouse.press(5, 5)
    myUi!!.mouse.dragTo(5, 0)
    assertThat(myChart!!.yRange.min).isWithin(EPSILON).of(15.0)
    assertThat(myChart!!.yRange.max).isWithin(EPSILON).of(15.0)
  }

  companion object {
    private val EPSILON = 1e-3
  }
}
