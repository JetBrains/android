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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProfilerClient
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.Arrays
import java.util.concurrent.TimeUnit

class EnergyEventsFetcherTest {
  private val events = ImmutableList.Builder<Common.Event>()
    .addAll(newEnergyEventGroup(1L, Arrays.asList(1000, 1300)))
    .addAll(newEnergyEventGroup(2L, Arrays.asList(1200)))
    .addAll(newEnergyEventGroup(3L, Arrays.asList(1300, 1400)))
    .build()
  private val energyService = FakeEnergyService(eventList = events)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyEventsFetcherTest", energyService)
  val myProfilerClient = ProfilerClient(grpcChannel.name)

  @Test
  fun expectCreateFetcherInitialFetchData() {
    val session = Common.Session.newBuilder().setSessionId(1234L).build()
    val fetcher = EnergyEventsFetcher(myProfilerClient.energyClient, session, Range(1000.0, 2000.0))
    var result: List<EnergyDuration> = ArrayList()
    val listener = EnergyEventsFetcher.Listener { list -> result = list }
    fetcher.addListener(listener)
    assertThat(result.size).isEqualTo(3)
  }

  @Test
  fun expectListenerDataAreCategorizedById() {
    val session = Common.Session.newBuilder().setSessionId(1234L).build()
    val fetcher = EnergyEventsFetcher(myProfilerClient.energyClient, session, Range(1000.0, 2000.0))
    var result: List<EnergyDuration> = ArrayList()
    val listener = EnergyEventsFetcher.Listener { list -> result = list }
    fetcher.addListener(listener)

    val firstDuration = result[0]
    assertThat(firstDuration.eventList.size).isEqualTo(2)
    assertThat(firstDuration.eventList[0].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1000))
    assertThat(firstDuration.eventList[1].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1300))
    val secondDuration = result[1]
    assertThat(secondDuration.eventList.size).isEqualTo(1)
    assertThat(secondDuration.eventList[0].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1200))
    val thirdDuration = result[2]
    assertThat(thirdDuration.eventList.size).isEqualTo(2)
    assertThat(thirdDuration.eventList[0].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1300))
    assertThat(thirdDuration.eventList[1].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1400))
  }

  @Test
  fun testFetchCompleteDurations() {
    val session = Common.Session.newBuilder().setSessionId(1234L).build()
    val fetcher = EnergyEventsFetcher(myProfilerClient.energyClient, session, Range(1299.0, 2000.0))
    var result: List<EnergyDuration> = ArrayList()
    val listener = EnergyEventsFetcher.Listener { list -> result = list }
    fetcher.addListener(listener)

    assertThat(result.size).isEqualTo(2)
    val firstDuration = result[0]
    assertThat(firstDuration.eventList.size).isEqualTo(2)
    assertThat(firstDuration.eventList[0].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1000))
    assertThat(firstDuration.eventList[1].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1300))
    val secondDuration = result[1]
    assertThat(secondDuration.eventList.size).isEqualTo(2)
    assertThat(secondDuration.eventList[0].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1300))
    assertThat(secondDuration.eventList[1].timestamp).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1400))
  }

  // Build an immutable list of default events with the same ID.
  private fun newEnergyEventGroup(id: Long, timeList: List<Long>): List<Common.Event> {
    return timeList.stream()
      .map { time -> Common.Event.newBuilder().setTimestamp(TimeUnit.MICROSECONDS.toNanos(time)).setGroupId(id).build() }
      .collect(ImmutableList.toImmutableList())
  }
}