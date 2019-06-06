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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProfilerClient
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Arrays
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
class EnergyEventsFetcherTest(private val useUnifiedEvents: Boolean) {
  companion object {
    const val STREAM_ID = 123L
    const val PID = 321

    @JvmStatic
    @Parameterized.Parameters
    fun useNewEventPipelineParameter() = listOf(false, true)
  }

  private val myEvents = ImmutableList.Builder<Common.Event>()
    .addAll(newEnergyEventGroup(1L, Arrays.asList(1000, 1300)))
    .addAll(newEnergyEventGroup(2L, Arrays.asList(1200)))
    .addAll(newEnergyEventGroup(3L, Arrays.asList(1300, 1400)))
    .build()
  private val mySession = Common.Session.newBuilder().setSessionId(1234L).setStreamId(STREAM_ID).setPid(PID).build()
  private val myEnergyService = FakeEnergyService(eventList = myEvents)
  private val myTransportService = FakeTransportService(FakeTimer())

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("EnergyEventsFetcherTest", myEnergyService, myTransportService)
  val myProfilerClient = ProfilerClient(myGrpcChannel.name)

  @Before
  fun setUp() {
    myEvents.forEach { event -> myTransportService.addEventToEventGroup(STREAM_ID, event) }
  }

  @Test
  fun expectCreateFetcherInitialFetchData() {
    val fetcher = EnergyEventsFetcher(myProfilerClient, mySession, Range(1000.0, 2000.0), useUnifiedEvents)
    var result: List<EnergyDuration> = ArrayList()
    val listener = EnergyEventsFetcher.Listener { list -> result = list }
    fetcher.addListener(listener)
    assertThat(result.size).isEqualTo(3)
  }

  @Test
  fun expectListenerDataAreCategorizedById() {
    val fetcher = EnergyEventsFetcher(myProfilerClient, mySession, Range(1000.0, 2000.0), useUnifiedEvents)
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
    val fetcher = EnergyEventsFetcher(myProfilerClient, mySession, Range(1299.0, 2000.0), useUnifiedEvents)
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
    var counter = 0
    return timeList.stream()
      .map { time ->
        Common.Event.newBuilder()
          .setPid(PID)
          .setTimestamp(TimeUnit.MICROSECONDS.toNanos(time))
          .setGroupId(id)
          .setKind(Common.Event.Kind.ENERGY_EVENT)
          // Mark last event is_ended = true.
          .setIsEnded(counter++ == timeList.size - 1)
          .build()
      }
      .collect(ImmutableList.toImmutableList())
  }
}