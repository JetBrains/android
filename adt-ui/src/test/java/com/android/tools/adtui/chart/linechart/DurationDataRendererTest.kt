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
import java.awt.geom.Rectangle2D
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

  private fun validateRegion(rect: Rectangle2D.Float, xStart: Float, yStart: Float, width: Float, height: Float) {
    assertThat(rect.x).isWithin(EPSILON).of(xStart)
    assertThat(rect.y).isWithin(EPSILON).of(yStart)
    assertThat(rect.width).isWithin(EPSILON).of(width)
    assertThat(rect.height).isWithin(EPSILON).of(height)
  }
}