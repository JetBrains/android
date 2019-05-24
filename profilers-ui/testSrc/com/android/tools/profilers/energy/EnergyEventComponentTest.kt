/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy

import com.android.tools.adtui.model.DefaultDataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.event.EventAction
import com.android.tools.adtui.model.event.EventModel
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D

class EnergyEventComponentTest {

  @Test
  fun rangeChanged() {
    val locationRequested = Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(1L)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationUpdateRequested(Energy.LocationUpdateRequested.getDefaultInstance()))
      .build()
    val locationChanged = Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(10L)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationChanged(Energy.LocationChanged.getDefaultInstance()))
      .build()
    val locationRequestRemoved = Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(20L)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationUpdateRemoved(Energy.LocationUpdateRemoved.getDefaultInstance()))
      .setIsEnded(true)
      .build()

    val range = Range(0.0, 100.0)
    val dataSeries = DefaultDataSeries<EventAction<Common.Event>>()
    dataSeries.add(1, EventAction(1, 10, locationRequested))
    dataSeries.add(10, EventAction(10, 20, locationChanged))
    dataSeries.add(20, EventAction(20, 30, locationRequestRemoved))
    val component = EnergyEventComponent(EventModel(RangedSeries(range, dataSeries)), Color.WHITE)
    val fakeGraphics = mock<Graphics2D>(Graphics2D::class.java)
    val dimension = Dimension(100, 20)

    range.set(5.0, 20.0)
    component.draw(fakeGraphics, dimension)
    assertThat(component.actionToDrawList.size).isEqualTo(3)
    assertThat(component.actionToDrawList[0].type.timestamp).isEqualTo(1L)
    assertThat(component.actionToDrawList[1].type.timestamp).isEqualTo(10L)
    assertThat(component.actionToDrawList[2].type.timestamp).isEqualTo(20L)

    range.set(5.0, 15.0)
    component.draw(fakeGraphics, dimension)
    assertThat(component.actionToDrawList.size).isEqualTo(2)
    assertThat(component.actionToDrawList[0].type.timestamp).isEqualTo(1L)
    assertThat(component.actionToDrawList[1].type.timestamp).isEqualTo(10L)

    range.set(10.0, 15.0)
    component.draw(fakeGraphics, dimension)
    assertThat(component.actionToDrawList.size).isEqualTo(1)
    assertThat(component.actionToDrawList[0].type.timestamp).isEqualTo(10L)
  }
}
