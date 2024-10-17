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
package com.android.tools.adtui.chart.linechart

import org.mockito.kotlin.whenever
import com.android.tools.adtui.model.DefaultDataSeries
import com.android.tools.adtui.model.DurationData
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.Interpolatable
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.Arrays
import java.util.Collections
import javax.swing.Icon
import javax.swing.JPanel

const val EPSILON: Float = 1e-6f

class DurationDataRendererTest {

  @Test
  fun testCursorIsSetToDefaultWhenMouseIsOverLabel() {
    // Setup some data for the attached data seres
    val xRange = Range(0.0, 10.0)
    val yRange = Range(0.0, 10.0)
    val attachedSeries = DefaultDataSeries<Long>()
    attachedSeries.add(4, 4)
    val attachedRangeSeries = RangedContinuousSeries("attached", xRange, yRange, attachedSeries)

    // Setup the duration data series
    val dataSeries = DefaultDataSeries<DurationData>()
    dataSeries.add(0, DurationData { 0 })
    val durationData = DurationDataModel(RangedSeries(xRange, dataSeries))
    durationData.setAttachedSeries(attachedRangeSeries, Interpolatable.SegmentInterpolator)
    durationData.setAttachPredicate { data -> data.x == 6L }

    // Creates the DurationDataRenderer and forces an update, which calculates the DurationData's normalized positioning.
    // Creates the DurationDataRenderer and forces an update, which calculates the DurationData's normalized positioning.
    val dummyIcon = object : Icon {
      override fun getIconHeight(): Int = 5
      override fun getIconWidth(): Int = 5
      override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit
    }

    val durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK).setIcon(dummyIcon).build()
    val underneathComponent = JPanel()
    val overlayComponent = OverlayComponent(underneathComponent)
    overlayComponent.bounds = Rectangle(0, 0, 200, 50)
    overlayComponent.addDurationDataRenderer(durationDataRenderer)

    durationData.update(1) // Forces duration data renderer to update

    assertThat(durationDataRenderer.clickRegionCache.size).isEqualTo(1)
    validateRegion(durationDataRenderer.clickRegionCache[0], 0f, 1f, 5f, 5f)       // attached series has no data before this point, y == 1.

    underneathComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    val fakeUi = FakeUi(overlayComponent)
    val clickRegionRect = durationDataRenderer.getScaledClickRegion(durationDataRenderer.clickRegionCache[0],
                                                                    200,
                                                                    50,
                                                                    !durationDataRenderer.regionOnLineSeries[0])
    fakeUi.mouse.moveTo(0, 0)
    assertThat(underneathComponent.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    fakeUi.mouse.moveTo(clickRegionRect.x.toInt() + 1, clickRegionRect.y.toInt() + 1)
    assertThat(underneathComponent.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
  }

  @Test
  fun testDurationDataPositioning() {
    // Setup some data for the attached data seres
    val xRange = Range(0.0, 10.0)
    val yRange = Range(0.0, 10.0)
    val attachedSeries = DefaultDataSeries<Long>()
    for (i in 4 until 10 step 2) {
      attachedSeries.add(i.toLong(), i.toLong())
    }

    val attachedRangeSeries = RangedContinuousSeries("attached", xRange, yRange, attachedSeries)

    // Setup the duration data series
    val dataSeries = DefaultDataSeries<DurationData>()
    for (i in 0 until 10 step 2) {
      dataSeries.add(i.toLong(), DurationData { 0 })
    }
    val durationData = DurationDataModel(RangedSeries(xRange, dataSeries))
    durationData.setAttachedSeries(attachedRangeSeries, Interpolatable.SegmentInterpolator)
    durationData.setAttachPredicate { data -> data.x >= 6L}

    // Creates the DurationDataRenderer and forces an update, which calculates the DurationData's normalized positioning.
    val mockIcon = mock(Icon::class.java)
    whenever(mockIcon.iconWidth).thenReturn(5)
    whenever(mockIcon.iconHeight).thenReturn(5)
    val durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK)
      .setIcon(mockIcon).setHostInsets(Insets(5, 10, 15, 20)).setClickRegionPadding(0, 0).build()
    durationData.update(-1)  // value doesn't matter here.

    assertThat(durationDataRenderer.clickRegionCache.size).isEqualTo(5)
    validateRegion(durationDataRenderer.clickRegionCache[0], 0f, 1f, 5f, 5f)       // attached series has no data before this point, y == 1.
    validateRegion(durationDataRenderer.clickRegionCache[1], 0.2f, 1f, 5f, 5f)    // attached series has no data before this point, y == 1.
    validateRegion(durationDataRenderer.clickRegionCache[2], 0.4f, 1f, 5f, 5f)    // attached predicate fails.
    validateRegion(durationDataRenderer.clickRegionCache[3], 0.6f, 0.4f, 5f, 5f)
    // attached series has no data after this point, use the last point as the attached y.
    validateRegion(durationDataRenderer.clickRegionCache[4], 0.8f, 0.2f, 5f, 5f)

    assert(durationDataRenderer.clickRegionCache.size == durationDataRenderer.regionOnLineSeries.size)

    // Also checked for the post-scaled values
    fun testPostScaled(hostWidth: Int, hostHeight: Int) = fun (i: Int, x: Float, y: Float) =
      validateRegion(durationDataRenderer.getScaledClickRegion(durationDataRenderer.clickRegionCache[i],
                                                               hostWidth,
                                                               hostHeight,
                                                               durationDataRenderer.regionOnLineSeries[i]),
                     x,
                     y,
                     5f,
                     5f)

    val testPostScaledWithEnoughRoomAt = testPostScaled(100, 100)

    // 10 pixel left inset, 15 pixel bottom inset + icon height
    testPostScaledWithEnoughRoomAt(0, 10f, 80f)
    // 10 pixel left inset + 0.2 * 70 pixels (after accounting for padding)
    // 15 pixel bottom inset + icon height
    testPostScaledWithEnoughRoomAt(1, 24f, 80f)
    // 10 pixel left inset + 0.4 * 70 pixels (after accounting for padding)
    // 15 pixel bottom inset + icon height
    testPostScaledWithEnoughRoomAt(2, 38f, 80f)
    // 10 pixel left inset + 0.6 * 70 pixels (after accounting for padding)
    // 15 pixel bottom inset + icon height + 0.4 * 80 pixels after accounting for padding)
    testPostScaledWithEnoughRoomAt(3, 52f, 32f)
    // 10 pixel left inset + 0.8 * 70 pixels (after accounting for padding)
    // 15 pixel bottom inset + icon height + 0.2 * 80 pixels after accounting for padding)
    testPostScaledWithEnoughRoomAt(4, 66f, 16f)

    // When the host is no tall enough:
    // - Labels not on line should respect the insets
    // - Labels on line should stick to the line
    val testPostScaledWithNotEnoughRoomAt = testPostScaled(100, 10)
    testPostScaledWithNotEnoughRoomAt(0, 10f, 5f)    // off
    testPostScaledWithNotEnoughRoomAt(1, 24f, 5f)    // off
    testPostScaledWithNotEnoughRoomAt(2, 38f, 5f)    // off
    testPostScaledWithNotEnoughRoomAt(3, 52f, -4f)   // on
    testPostScaledWithNotEnoughRoomAt(4, 66f, -2f)   // on
  }

  @Test
  fun testDashPhaseMatchingOnCustomConfig() {
    val xRange = Range(0.0, 10.0)
    val yRange = Range(0.0, 10.0)
    val series1 = DefaultDataSeries<Long>()
    val series2 = DefaultDataSeries<Long>()
    val rangeSeries1 = RangedContinuousSeries("test1", xRange, yRange, series1)
    val rangeSeries2 = RangedContinuousSeries("test2", xRange, yRange, series2)
    val lineChart = LineChart(Arrays.asList(rangeSeries1, rangeSeries2))
    lineChart.configure(rangeSeries1, LineConfig(Color.ORANGE).setStroke(LineConfig.DEFAULT_DASH_STROKE))
    lineChart.configure(rangeSeries2, LineConfig(Color.PINK).setStroke(LineConfig.DEFAULT_DASH_STROKE))

    val dataSeries = DefaultDataSeries<DurationData>()
    val durationData = DurationDataModel(RangedSeries(xRange, dataSeries))
    val durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK).build()
    durationDataRenderer.addCustomLineConfig(rangeSeries1, LineConfig(Color.BLUE).setStroke(LineConfig.DEFAULT_DASH_STROKE))
    durationDataRenderer.addCustomLineConfig(rangeSeries2, LineConfig(Color.YELLOW))

    // Fake a dash phase update on the default LineConfig
    lineChart.getLineConfig(rangeSeries1).adjustedDashPhase = 0.25
    lineChart.getLineConfig(rangeSeries2).adjustedDashPhase = 0.75

    assertThat(lineChart.getLineConfig(rangeSeries1).adjustedDashPhase).isWithin(EPSILON.toDouble()).of(0.25)
    assertThat(lineChart.getLineConfig(rangeSeries2).adjustedDashPhase).isWithin(EPSILON.toDouble()).of(0.75)
    assertThat(durationDataRenderer.getCustomLineConfig(rangeSeries1).adjustedDashPhase).isWithin(EPSILON.toDouble()).of(0.0)
    assertThat(durationDataRenderer.getCustomLineConfig(rangeSeries2).adjustedDashPhase).isWithin(EPSILON.toDouble()).of(0.0)

    // Fake a renderLines call then check that the dash phase on the custom LineConfig has been updated.
    durationDataRenderer.renderLines(lineChart,
                                     mock(Graphics2D::class.java),
                                     Collections.singletonList(Path2D.Float()) as List<Path2D>,
                                     Arrays.asList(rangeSeries1, rangeSeries2))
    assertThat(durationDataRenderer.getCustomLineConfig(rangeSeries1).adjustedDashPhase).isWithin(EPSILON.toDouble()).of(0.25)
    // rangeSeries2 isn't updated as the custom LineConfig is not a dash stroke.
    assertThat(durationDataRenderer.getCustomLineConfig(rangeSeries2).adjustedDashPhase).isWithin(EPSILON.toDouble()).of(0.0)
  }

  @Test
  fun testGetIcon() {
    val xRange = Range(0.0, 10.0)
    val dataSeries = DefaultDataSeries<DurationData>()
    dataSeries.add(0, DurationData { 0 })
    val durationData = DurationDataModel(RangedSeries(xRange, dataSeries))
    val dummyIcon = object : Icon {
      override fun getIconHeight(): Int = 1
      override fun getIconWidth(): Int = 1
      override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit
    }
    val dummyIcon2 = object : Icon {
      override fun getIconHeight(): Int = 2
      override fun getIconWidth(): Int = 2
      override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit
    }

    var durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK).setIcon(dummyIcon).build()
    assertThat(durationDataRenderer.getIcon(DurationData { 0 })).isEqualTo(dummyIcon)

    durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK).setIconMapper { _ -> dummyIcon2 }.build()
    assertThat(durationDataRenderer.getIcon(DurationData { 0 })).isEqualTo(dummyIcon2)
  }

  @Test
  fun backgroundClickable() {
    // Setup some data for the attached data seres
    val xRange = Range(0.0, 10.0)
    val yRange = Range(0.0, 10.0)
    val attachedSeries = DefaultDataSeries<Long>()
    attachedSeries.add(4, 4)
    val attachedRangeSeries = RangedContinuousSeries("attached", xRange, yRange, attachedSeries)
    var clicked = false

    // Setup the duration data series
    val dataSeries = DefaultDataSeries<DurationData>()
    dataSeries.add(5, DurationData { 1 })
    val durationData = DurationDataModel(RangedSeries(xRange, dataSeries))
    durationData.setAttachedSeries(attachedRangeSeries, Interpolatable.SegmentInterpolator)
    durationData.setAttachPredicate { data -> data.x == 6L }
    val durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK).setBackgroundClickable(
      true).setClickHandler { clicked = true }.build()
    val underneathComponent = JPanel()
    val overlayComponent = OverlayComponent(underneathComponent)
    overlayComponent.bounds = Rectangle(0, 0, 200, 50)
    overlayComponent.addDurationDataRenderer(durationDataRenderer)

    durationData.update(1) // Forces duration data renderer to update
    val fakeUi = FakeUi(overlayComponent)
    // Right-click shouldn't trigger the click handler.
    fakeUi.mouse.rightClick(100, 0)
    assertThat(clicked).isFalse()
    // Left-click should trigger the click handler.
    fakeUi.mouse.click(100, 0)
    assertThat(clicked).isTrue()
  }

  private fun validateRegion(rect: Rectangle2D.Float, xStart: Float, yStart: Float, width: Float, height: Float) {
    assertThat(rect.x).isWithin(EPSILON).of(xStart)
    assertThat(rect.y).isWithin(EPSILON).of(yStart)
    assertThat(rect.width).isWithin(EPSILON).of(width)
    assertThat(rect.height).isWithin(EPSILON).of(height)
  }
}