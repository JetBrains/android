/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.Event
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

class NetworkInspectorDataSeriesTest {
  @Test
  fun getDataForRange() {
    val event1 =
      Event.newBuilder().setTimestamp(1000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val event2 =
      Event.newBuilder().setTimestamp(2000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val source = FakeNetworkInspectorDataSource(speedEventList = listOf(event1, event2))

    val foundEvents = mutableListOf<Event>()
    val series = NetworkInspectorDataSeries(source) { event -> foundEvents.add(event) }
    series.getDataForRange(Range(1.0, 2.0))

    assertThat(foundEvents).containsExactly(event1, event2)
  }
}
