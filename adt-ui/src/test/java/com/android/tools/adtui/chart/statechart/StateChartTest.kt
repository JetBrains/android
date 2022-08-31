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
package com.android.tools.adtui.chart.statechart

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

class StateChartTest {
  @Test
  fun emptyStateChartShouldNotThrowException() {
    val model = StateChartModel<Nothing>()
    val dataSeries = DataSeries.empty<Nothing>()
    model.addSeries(RangedSeries(Range(0.0, 100.0), dataSeries))
    val stateChart = StateChart(model, mapOf())
    stateChart.setSize(100, 100)
    val fakeGraphics = Mockito.mock(Graphics2D::class.java)
    whenever(fakeGraphics.create()).thenReturn(fakeGraphics)
    stateChart.paint(fakeGraphics)
  }

  @Test
  fun testStateChartTextConverter() {
    val model = StateChartModel<Int>()
    val dataSeries = DataSeries.using { listOf(SeriesData(0, 1), SeriesData(1000, 2)) }
    model.addSeries(RangedSeries(Range(0.0, 100.0), dataSeries))
    val stateChart = StateChart(model, constColorProvider(Color.BLACK), { "123" })
    stateChart.setSize(100, 100)
    val fakeGraphics = Mockito.mock(Graphics2D::class.java)
    whenever(fakeGraphics.create()).thenReturn(fakeGraphics)
    stateChart.paint(fakeGraphics)
    Mockito.verify(fakeGraphics, Mockito.times(1))
      .drawString(ArgumentMatchers.eq("123"), ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
  }

  @Test
  fun testStateChartWithDefaultTextConverterUsesToString() {
    val model = StateChartModel<ToStringTestClass>()
    val dataSeries = DataSeries.using {
      listOf(SeriesData(0, ToStringTestClass("Test")), SeriesData(1000, ToStringTestClass("Test2")))
    }
    model.addSeries(RangedSeries(Range(0.0, 100.0), dataSeries))
    val stateChart = StateChart(model, constColorProvider(Color.BLACK), StateChart.defaultTextConverter())
    stateChart.setSize(100, 100)
    val fakeGraphics = Mockito.mock(Graphics2D::class.java)
    whenever(fakeGraphics.create()).thenReturn(fakeGraphics)
    stateChart.paint(fakeGraphics)
    Mockito.verify(fakeGraphics, Mockito.times(1))
      .drawString(ArgumentMatchers.eq("Test"), ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
  }

  private class ToStringTestClass(private val myString: String) {
    override fun toString() = myString
  }

  @Test
  fun testLargeValuesGetOverlappedAsOne() {
    val model = StateChartModel<Long>()
    val dataSeries = DataSeries.using {
      listOf(SeriesData(100, 0L), SeriesData(101, 1L), SeriesData(105, 2L))
    }
    val colorMap = mapOf(0L to Color.RED,
                         1L to Color.GREEN,
                         2L to Color.BLUE)
    model.addSeries(RangedSeries(Range(0.0, Long.MAX_VALUE.toDouble()), dataSeries))
    val stateChart = StateChart(model, colorMap)
    stateChart.setSize(100, 100)
    val fakeGraphics = Mockito.mock(Graphics2D::class.java)
    whenever(fakeGraphics.create()).thenReturn(fakeGraphics)
    stateChart.paint(fakeGraphics)

    // Because between 0 -> Max Long, values 100 and 101 are so close we end up with a floating point
    // rounding error when creating the rectangle. As such we end up creating two rectangles
    // on top of each ohter and storing them in our map of rectangles to values.
    // This means we do not draw the first value instead we throw it out and draw only
    // the second value.
    // As such we expect 2 rectangles one with the color GREEN, the other with the color BLUE.
    Mockito.verify(fakeGraphics, Mockito.times(2)).fill(ArgumentMatchers.any())
    Mockito.verify(fakeGraphics, Mockito.times(1)).color = Color.GREEN
    Mockito.verify(fakeGraphics, Mockito.times(1)).color = Color.BLUE
    Mockito.verify(fakeGraphics, Mockito.times(0)).color = Color.RED
  }

  @Test
  fun `click-listener called on the right state item`() {
    val model = StateChartModel<Long>()
    model.addSeries(RangedSeries(Range(0.0, 10.0), DataSeries.using { longArrayOf(0, 2, 4, 6, 8, 10).map { SeriesData(it, it) } }))
    model.addSeries(RangedSeries(Range(0.0, 10.0), DataSeries.using { longArrayOf(1, 3, 5, 7, 9).map { SeriesData(it, it) } }))

    val stateChart = StateChart(model, constColorProvider(Color.PINK)).apply {
      setSize(100, 100)
    }

    // --1---3---5---7---9--
    // 0---2---4---6---8---10
    assertThat(stateChart.itemAtMouse(Point(5, 25))).isEqualTo(null)
    assertThat(stateChart.itemAtMouse(Point(95, 25))).isEqualTo(9)
    assertThat(stateChart.itemAtMouse(Point(25, 25))).isEqualTo(1)
    assertThat(stateChart.itemAtMouse(Point(10, 80))).isEqualTo(0)
    assertThat(stateChart.itemAtMouse(Point(75, 20))).isEqualTo(7)
    assertThat(stateChart.itemAtMouse(Point(75, 80))).isEqualTo(6)

    assertThat(stateChart.itemAtMouse(Point(5, 200))).isEqualTo(null)
    assertThat(stateChart.itemAtMouse(Point(5, 101))).isEqualTo(0)
    assertThat(stateChart.itemAtMouse(Point(25, -1))).isEqualTo(1)
    assertThat(stateChart.itemAtMouse(Point(25, -100))).isEqualTo(null)
  }

  @Test
  fun `series at mouse gives right-most index to mouse's left`() {
    val model = StateChartModel<Long>()
    model.addSeries(RangedSeries(Range(0.0, 10.0), DataSeries.using { longArrayOf(0, 2, 4, 6, 8, 10).map { SeriesData(it, it) } }))
    model.addSeries(RangedSeries(Range(0.0, 10.0), DataSeries.using { longArrayOf(1, 3, 5, 7, 9).map { SeriesData(it, it) } }))

    val stateChart = StateChart(model, constColorProvider(Color.PINK)).apply {
      setSize(100, 100)
    }

    // --1---3---5---7---9--
    // 0---2---4---6---8---10
    assertThat(stateChart.seriesIndexAtMouse(Point(5, 25))).isEqualTo(1 to -1)
    assertThat(stateChart.seriesIndexAtMouse(Point(95, 25))).isEqualTo(1 to 4)
    assertThat(stateChart.seriesIndexAtMouse(Point(25, 25))).isEqualTo(1 to 0)
    assertThat(stateChart.seriesIndexAtMouse(Point(10, 80))).isEqualTo(0 to 0)
    assertThat(stateChart.seriesIndexAtMouse(Point(75, 20))).isEqualTo(1 to 3)
    assertThat(stateChart.seriesIndexAtMouse(Point(75, 80))).isEqualTo(0 to 3)

    assertThat(stateChart.seriesIndexAtMouse(Point(5, 200))).isEqualTo(null)
    assertThat(stateChart.seriesIndexAtMouse(Point(5, 101))).isEqualTo(0 to 0)
    assertThat(stateChart.seriesIndexAtMouse(Point(25, -1))).isEqualTo(1 to 0)
    assertThat(stateChart.seriesIndexAtMouse(Point(25, -100))).isEqualTo(null)
  }

  @Test
  fun `chart uses custom renderer`() {
    val model = StateChartModel<Long>()
    model.addSeries(RangedSeries(Range(0.0, 10.0), DataSeries.using { longArrayOf(0, 2, 4, 6, 8, 10).map { SeriesData(it, it) } }))
    model.addSeries(RangedSeries(Range(0.0, 10.0), DataSeries.using { longArrayOf(1, 3, 5, 7, 9).map { SeriesData(it, it) } }))

    fun render(g: Graphics2D, rect: Rectangle2D.Float, defaultFontMetrics: FontMetrics, hovered: Boolean, value: Long) {
      if (value % 2 == 0L) g.fill(rect) else g.drawString("hi", 25, 25)
    }

    val stateChart = StateChart(model, ::render).apply { setSize(100, 100) }

    val fakeGraphics = Mockito.mock(Graphics2D::class.java)
    whenever(fakeGraphics.create()).thenReturn(fakeGraphics)
    stateChart.paint(fakeGraphics)
    Mockito.verify(fakeGraphics, Mockito.times(5))
      .drawString(ArgumentMatchers.eq("hi"), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())
    Mockito.verify(fakeGraphics, Mockito.times(5))
      .fill(ArgumentMatchers.any(Rectangle2D.Float::class.java))
  }
}

private fun<T> constColorProvider(color: Color) = object : StateChartColorProvider<T>() {
  override fun getColor(isMouseOver: Boolean, value: T) = color
}