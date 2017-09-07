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

import com.android.tools.adtui.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.*
import javax.swing.Icon

const val EPSILON: Float = 1e-6f

class DurationDataRendererTest {

  @Test
  fun testDurationDataPositioning() {
    // Setup some data for the attached data seres
    val xRange = Range(0.0, 10.0)
    val yRange = Range(0.0, 10.0)
    val attachedSeries = DefaultDataSeries<Long>()
    run {
      var i = 4
      while (i < 10) {
        attachedSeries.add(i.toLong(), i.toLong())
        i += 2
      }
    }
    val attachedRangeSeries = RangedContinuousSeries("attached", xRange, yRange, attachedSeries)

    // Setup the duration data series
    val dataSeries = DefaultDataSeries<DurationData>()
    run {
      var i = 0
      while (i < 10) {
        dataSeries.add(i.toLong(), DurationData { 0 })
        i += 2
      }
    }
    val durationData = DurationDataModel(RangedSeries(xRange, dataSeries))
    durationData.setAttachedSeries(attachedRangeSeries, Interpolatable.SegmentInterpolator)

    // Creates the DurationDataRenderer and forces an update, which calculates the DurationData's normalized positioning.
    val mockIcon = mock(Icon::class.java)
    `when`(mockIcon.iconWidth).thenReturn(5)
    `when`(mockIcon.iconHeight).thenReturn(5)
    val durationDataRenderer = DurationDataRenderer.Builder(durationData, Color.BLACK)
        .setIcon(mockIcon).build()
    durationData.update(-1)  // value doesn't matter here.

    val regions = durationDataRenderer.GetDurationDataRegions()
    assertThat(regions.size).isEqualTo(5)
    validateRegion(regions[0], 0f, 1f, 5f, 5f)       // attached series has no data before this point, y == 1.
    validateRegion(regions[1], 0.2f, 1f, 5f, 5f)    // attached series has no data before this point, y == 1.
    validateRegion(regions[2], 0.4f, 0.6f, 5f, 5f)
    validateRegion(regions[3], 0.6f, 0.4f, 5f, 5f)
    validateRegion(regions[4], 0.8f, 1f, 5f, 5f)   // attached series has no data after this point. y == 1.
  }

  @Test
  fun testDashPhaseMatchingOnCustomConfig() {
    val xRange = Range(0.0, 10.0)
    val yRange = Range(0.0, 10.0)
    val series1 = DefaultDataSeries<Long>()
    val series2 = DefaultDataSeries<Long>()
    val rangeSeries1 = RangedContinuousSeries("test1", xRange, yRange, series1)
    val rangeSeries2 = RangedContinuousSeries("test2", xRange, yRange, series2)
    var lineChart = LineChart(Arrays.asList(rangeSeries1, rangeSeries2))
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

  private fun validateRegion(rect: Rectangle2D.Float, xStart: Float, yStart: Float, width: Float, height: Float) {
    assertThat(rect.x).isWithin(EPSILON).of(xStart)
    assertThat(rect.y).isWithin(EPSILON).of(yStart)
    assertThat(rect.width).isWithin(EPSILON).of(width)
    assertThat(rect.height).isWithin(EPSILON).of(height)
  }
}