/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.poller

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.pipeline.example.proto.Echo
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TransportEventPollerTest {

  private var timer = FakeTimer()
  private var transportService = FakeTransportService(timer, true)
  private var transportClient: TransportClient? = null
  private var transportEventPoller: TransportEventPoller? = null

  @get:Rule
  val grpcServer = FakeGrpcServer.createFakeGrpcServer("TransportEventPollerTestChannel", transportService)

  @Before
  fun createPoller() {
    transportClient = TransportClient(grpcServer.name)
    transportEventPoller = TransportEventPoller(transportClient!!.transportStub)
  }

  @After
  fun close() {
    transportEventPoller?.let { TransportEventPoller.stopPoller(it) }
    transportEventPoller = null
    transportClient!!.shutdown()
    transportClient = null
  }

  private fun generateEchoEvent(ts: Long) = Common.Event.newBuilder()
    .setTimestamp(ts)
    .setKind(Common.Event.Kind.ECHO)
    .build()

  /**
   * Tests that a newly created listener with already-connected device+process
   * will receive the stream connected and process started events
   */
  @Test
  fun testStreamAndProcessListeners() {
    var streamEventSeen = 0

    // Create listener for STREAM connected
    val streamConnectedListener = TransportEventListener(
      eventKind = Common.Event.Kind.STREAM,
      callback = { event ->
        assertThat(event.stream.streamConnected.stream.streamId).isEqualTo(FakeTransportService.FAKE_DEVICE_ID)
        streamEventSeen++
        false
      },
      executor = MoreExecutors.directExecutor(),
      filter = { event -> event.stream.hasStreamConnected() })
    transportEventPoller!!.registerListener(streamConnectedListener)
    transportEventPoller!!.poll()

    // Create listener for PROCESS started
    var processEventSeen = 0
    val processStartedListener = TransportEventListener(
      eventKind = Common.Event.Kind.PROCESS,
      callback = { event ->
        assertThat(event.process.processStarted.process.pid).isEqualTo(1)
        assertThat(event.process.processStarted.process.deviceId).isEqualTo(FakeTransportService.FAKE_DEVICE_ID)
        processEventSeen++
        false
      }, executor = MoreExecutors.directExecutor(),
      filter = { event -> event.process.hasProcessStarted() })
    transportEventPoller!!.registerListener(processStartedListener)
    transportEventPoller!!.poll()

    assertThat(streamEventSeen).isEqualTo(1)
    assertThat(processEventSeen).isEqualTo(1)
  }

  /**
   * Tests that listener receives events from both before and after it was created
   */
  @Test
  fun testEventListeners() {
    var eventsSeen = 0
    val expectedEvents = ArrayList<Common.Event>()

    // First event exists before listener is registered
    val echoEvent = Common.Event.newBuilder()
      .setTimestamp(0)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(123)
      .build()
    expectedEvents.add(echoEvent)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvent)

    // Create listener for ECHO event
    val echoListener = TransportEventListener(eventKind = Common.Event.Kind.ECHO,
                                              callback = { event ->
                                                assertThat(event).isEqualTo(expectedEvents.removeAt(0))
                                                eventsSeen++
                                                false
                                              },
                                              executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(echoListener)
    transportEventPoller!!.poll()

    // Second event created after first is received
    val echoEvent2 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(456)
      .build()
    expectedEvents.add(echoEvent2)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvent2)

    // Third event with the same group ID
    val echoEvent3 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(456)
      .build()
    expectedEvents.add(echoEvent3)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvent3)
    transportEventPoller!!.poll()

    // Receive the last 2 events
    assertThat(eventsSeen).isEqualTo(3)
  }

  /**
   * Tests that a registered listener is removed when the callback returns true;
   */
  @Test
  fun testRemoveEventListener() {
    val echoEvents = mutableListOf<Common.Event>()

    // Create listener for ECHO event that should remove itself after 3 callbacks.
    var receivedEventsCount1 = 0
    val echoListener1 = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      startTime = { 0L },
      endTime = { 20L },
      callback = { event ->
        assertThat(event).isEqualTo(echoEvents[receivedEventsCount1])
        receivedEventsCount1++
        receivedEventsCount1 == 3
      },
      executor = MoreExecutors.directExecutor()
    )
    var receivedEventsCount2 = 0
    val echoListener2 = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      startTime = { 0L },
      endTime = { 20L },
      callback = { event ->
        assertThat(event).isEqualTo(echoEvents[receivedEventsCount2])
        receivedEventsCount2++
        false
      },
      executor = MoreExecutors.directExecutor()
    )

    transportEventPoller!!.registerListener(echoListener1)
    transportEventPoller!!.registerListener(echoListener2)

    // Generate 10 events
    for (time in 1L..10L) {
      val event = generateEchoEvent(time)
      transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, event)
      echoEvents.add(event)
      transportEventPoller!!.poll()
    }

    // we should have stopped triggering the callback after 3 counts.
    assertThat(receivedEventsCount1).isEqualTo(3)
    assertThat(receivedEventsCount2).isEqualTo(10)
  }

  @Test
  fun pollerTracksEventListenerTimestamp() {
    var event10Seen = 0
    var event20Seen = 0
    var eventsPicked = 0
    TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      startTime = { 5L },
      callback = {
        eventsPicked++
        if (it.timestamp == 10L) {
          event10Seen++
        }
        else if (it.timestamp == 20L) {
          event20Seen++
        }
        false
      },
      executor = MoreExecutors.directExecutor()
    ).also { transportEventPoller!!.registerListener(it) }

    // Add event with timestamp 2. This shouldn't be picked up by poller because listener was ts=5
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(2))
    // Add event with timestamp 10. This should be picked up by poller because listener was ts=5
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(10))
    transportEventPoller!!.poll()
    assertThat(eventsPicked).isEqualTo(1)
    assertThat(event10Seen).isEqualTo(1)
    assertThat(event20Seen).isEqualTo(0)

    // Add event with timestamp 8. This shouldn't be picked up by poller because last seen event was ts=10.
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(8))
    eventsPicked = 0
    transportEventPoller!!.poll()
    assertThat(eventsPicked).isEqualTo(0)
    assertThat(event10Seen).isEqualTo(1)
    assertThat(event20Seen).isEqualTo(0)

    // Add event with timestamp 20. This should be picked up by poller because last seen event was ts=10.
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(20))
    eventsPicked = 0
    transportEventPoller!!.poll()
    assertThat(eventsPicked).isEqualTo(1)
    assertThat(event10Seen).isEqualTo(1)
    assertThat(event20Seen).isEqualTo(1)
  }

  /**
   * Tests that listeners receive the right events
   */
  private fun checkNonCustomFilter(
    eventKind: Common.Event.Kind? = null,
    groupId: Long? = null,
    processId: Int? = null
  ) {
    val positiveEvents = mutableListOf<Common.Event>()
    val negativeEvents = mutableListOf<Common.Event>()
    val otherEventKind = if (eventKind != null) {
      // get the next kind, but skip 0 (so wrap one place early and then add one after)
      val nextKindId = eventKind.ordinal.rem(Common.Event.Kind.values().size - 1) + 1
      Common.Event.Kind.values()[nextKindId]
    }
    else {
      Common.Event.Kind.ECHO
    }
    val otherGroupId = groupId?.let { it + 1 } ?: 0
    val otherProcessId = processId?.let { it + 1 } ?: 0

    val realEventKind = eventKind ?: Common.Event.Kind.ECHO
    val realGroupId = groupId ?: 0
    val realProcessId = processId ?: 0

    val positiveEventListener = TransportEventListener(
      streamId = { 1 },
      eventKind = realEventKind,
      groupId = { realGroupId },
      processId = { realProcessId },
      callback = { event ->
        assertThat(event.pid).isEqualTo(realProcessId)
        assertThat(event.groupId).isEqualTo(realGroupId)
        assertThat(event.kind).isEqualTo(realEventKind)
        positiveEvents.add(event)
        false
      },
      executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(positiveEventListener)

    val negativeEventListener = TransportEventListener(
      streamId = { 1 },
      eventKind = otherEventKind,
      groupId = { otherGroupId },
      processId = { otherProcessId },
      callback = { event ->
        assertThat(event.pid).isEqualTo(otherProcessId)
        assertThat(event.groupId).isEqualTo(otherGroupId)
        assertThat(event.kind).isEqualTo(otherEventKind)
        negativeEvents.add(event)
        false
      },
      executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(negativeEventListener)

    val positiveEvent1 =
      Common.Event.newBuilder()
        .setTimestamp(1)
        .setKind(realEventKind)
        .setGroupId(realGroupId)
        .setPid(realProcessId)
        .build()

    val negativeEvent1 = Common.Event.newBuilder()
      .setTimestamp(2)
      .setKind(otherEventKind)
      .setGroupId(otherGroupId)
      .setPid(otherProcessId)
      .build()

    val positiveEvent2 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(realEventKind)
      .setGroupId(realGroupId)
      .setPid(realProcessId)
      .build()

    val negativeEvent2 = Common.Event.newBuilder()
      .setTimestamp(4)
      .setKind(otherEventKind)
      .setGroupId(otherGroupId)
      .setPid(otherProcessId)
      .build()

    transportService.addEventToStream(1L, positiveEvent1)
    transportService.addEventToStream(1L, negativeEvent1)
    transportService.addEventToStream(1L, positiveEvent2)
    transportService.addEventToStream(1L, negativeEvent2)

    transportEventPoller!!.poll()
    assertThat(positiveEvents.size).isEqualTo(2)
    assertThat(positiveEvents[0]).isEqualTo(positiveEvent1)
    assertThat(positiveEvents[1]).isEqualTo(positiveEvent2)
    assertThat(negativeEvents.size).isEqualTo(2)
    assertThat(negativeEvents[0]).isEqualTo(negativeEvent1)
    assertThat(negativeEvents[1]).isEqualTo(negativeEvent2)
  }

  @Test
  fun testKindFilter() {
    checkNonCustomFilter(eventKind = Common.Event.Kind.CPU_USAGE)
  }

  @Test
  fun testProcessFilter() {
    checkNonCustomFilter(processId = 123)
  }

  @Test
  fun testGroupFilter() {
    checkNonCustomFilter(groupId = 321L)
  }

  @Test
  fun testAllFilters() {
    checkNonCustomFilter(eventKind = Common.Event.Kind.CPU_USAGE, processId = 123, groupId = 321L)
  }

  @Test
  fun testCustomFilter() {
    val events = mutableListOf<Common.Event>()
    val positiveEventListener = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      streamId = { 1 },
      filter = { event -> event.echo.data == "blah" },
      callback = { event ->
        assertThat(event.echo.data).isEqualTo("blah")
        events.add(event)
        false
      },
      executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(positiveEventListener)

    val positiveEvent1 =
      Common.Event.newBuilder()
        .setTimestamp(1)
        .setKind(Common.Event.Kind.ECHO)
        .setEcho(Echo.EchoData.newBuilder().setData("blah"))
        .build()

    val negativeEvent1 = Common.Event.newBuilder()
      .setTimestamp(2)
      .setKind(Common.Event.Kind.ECHO)
      .setEcho(Echo.EchoData.newBuilder().setData("foo"))
      .build()

    val negativeEvent2 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .build()

    val positiveEvent2 = Common.Event.newBuilder()
      .setTimestamp(4)
      .setKind(Common.Event.Kind.ECHO)
      .setEcho(Echo.EchoData.newBuilder().setData("blah"))
      .build()

    transportService.addEventToStream(1L, positiveEvent1)
    transportService.addEventToStream(1L, negativeEvent1)
    transportService.addEventToStream(1L, negativeEvent2)
    transportService.addEventToStream(1L, positiveEvent2)

    transportEventPoller!!.poll()
    assertThat(events.size).isEqualTo(2)
    assertThat(events[0]).isEqualTo(positiveEvent1)
    assertThat(events[1]).isEqualTo(positiveEvent2)
  }

  @Test
  fun testLastTimestampNotRecordedIfListenerIsNotRegistered() {
    var operation = {}
    val events = mutableListOf<Common.Event>()
    val listener = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      executor = MoreExecutors.directExecutor(),
      callback = {
        events.add(it)
        operation()
        false
      })
    // Simulate that the listener is being unregistered during a poll:
    operation = { transportEventPoller!!.unregisterListener(listener) }

    val event1 = Common.Event.newBuilder().apply {
      timestamp = 4
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().apply { data = "blah" }.build()
    }.build()
    transportService.addEventToStream(1L, event1)

    // Register the listener and simulate an event from a device at time = 4
    transportEventPoller!!.registerListener(listener)
    transportEventPoller!!.poll()

    assertThat(events.size).isEqualTo(1)
    assertThat(events[0]).isEqualTo(event1)

    // Register the same listener and simulate an event from a different device at time = 1
    events.clear()
    operation = {}
    val event2 = Common.Event.newBuilder().apply {
      timestamp = 1
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().apply { data = "doh" }.build()
    }.build()
    transportService.addEventToStream(1L, event2)
    transportEventPoller!!.registerListener(listener)
    transportEventPoller!!.poll()

    assertThat(events.size).isEqualTo(2)
    assertThat(events[0]).isEqualTo(event2)
    assertThat(events[1]).isEqualTo(event1)
  }
}
