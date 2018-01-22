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

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profilers.FakeGrpcChannel
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.collections.ArrayList

class EnergyEventsFetcherTest {
  private val energyService = FakeEnergyService()
  private val events = ImmutableList.Builder<EnergyProfiler.EnergyEvent>()
    .addAll(newEnergyEvent(Arrays.asList(1000, 1300)))
    .addAll(newEnergyEvent(Arrays.asList(1200)))
    .addAll(newEnergyEvent(Arrays.asList(1300, 1400)))
    .build()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyEventsFetcherTest", energyService)

  @Test
  fun expectCreateFetcherInitialFetchData() {
    energyService.setEvents(events)
    val session = Common.Session.newBuilder().setSessionId(1234L).build()
    val fetcher = EnergyEventsFetcher(grpcChannel.client.energyClient, session, Range(1000.0, 2000.0))
    var result: List<EventDuration> = ArrayList()
    val listener = EnergyEventsFetcher.Listener { list -> result = list }
    fetcher.addListener(listener)
    assertThat(result.size).isEqualTo(3)
  }

  @Test
  fun expectListenerDataAreCategorizedById() {
    energyService.setEvents(events)
    val session = Common.Session.newBuilder().setSessionId(1234L).build()
    val fetcher = EnergyEventsFetcher(grpcChannel.client.energyClient, session, Range(1000.0, 2000.0))
    var result: List<EventDuration> = ArrayList()
    val listener = EnergyEventsFetcher.Listener { list -> result = list }
    fetcher.addListener(listener)

    val firstDuration = result.get(0)
    assertThat(firstDuration.eventList.size).isEqualTo(2)
    assertThat(firstDuration.eventList.get(0).timestamp).isEqualTo(1000)
    assertThat(firstDuration.eventList.get(1).timestamp).isEqualTo(1300)
    val secondDuration = result.get(1)
    assertThat(secondDuration.eventList.size).isEqualTo(1)
    assertThat(secondDuration.eventList.get(0).timestamp).isEqualTo(1200)
    val thirdDuration = result.get(2)
    assertThat(thirdDuration.eventList.size).isEqualTo(2)
    assertThat(thirdDuration.eventList.get(0).timestamp).isEqualTo(1300)
    assertThat(thirdDuration.eventList.get(1).timestamp).isEqualTo(1400)
  }

  private fun newEnergyEvent(timeList: List<Long>): List<EnergyProfiler.EnergyEvent> {
    val id = timeList.get(0).toInt()
    return timeList.stream().map { time -> EnergyProfiler.EnergyEvent.newBuilder().setTimestamp(time).setEventId(id).build() }
      .collect(ImmutableList.toImmutableList())
  }
}