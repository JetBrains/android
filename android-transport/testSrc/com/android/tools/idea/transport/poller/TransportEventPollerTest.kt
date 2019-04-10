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


import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.fail

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.MoreExecutors
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

private const val TIMEOUT_MILLISECONDS: Long = 10000

class TransportEventPollerTest {

  private var timer = FakeTimer()
  private var transportService = FakeTransportService(timer, true)

  @get:Rule
  val grpcServer = FakeGrpcServer.createFakeGrpcServer("TransportEventPollerTestChannel", transportService, transportService)!!

  /**
   * Tests that a newly created listener with already-connected device+process
   * will receive the stream connected and process started events
   */
  @Test
  fun testStreamAndProcessListeners() {
    val transportClient = TransportClient(grpcServer.name)
    val latch = CountDownLatch(2)
    val transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250))

    // Create listener for STREAM connected
    val streamConnectedListener = TransportEventListener(
      eventKind = Common.Event.Kind.STREAM,
      callback = { event ->
        assertThat(event.stream.streamConnected.stream.streamId).isEqualTo(FakeTransportService.FAKE_DEVICE_ID)
        latch.countDown()
      },
      executor = MoreExecutors.directExecutor(),
      filter = { event -> event.stream.hasStreamConnected() })
    transportEventPoller.registerListener(streamConnectedListener)

    // Create listener for PROCESS started
    val processStartedListener = TransportEventListener(
      eventKind = Common.Event.Kind.PROCESS,
      callback = { event ->
        assertThat(event.process.processStarted.process.pid).isEqualTo(1)
        assertThat(event.process.processStarted.process.deviceId).isEqualTo(FakeTransportService.FAKE_DEVICE_ID)
        latch.countDown()
      }, executor = MoreExecutors.directExecutor(),
      filter = { event -> event.process.hasProcessStarted() })
    transportEventPoller.registerListener(processStartedListener)

    // Receive
    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

  }

  /**
   * Tests that listener receives events from both before and after it was created
   */
  @Test
  fun testEventListeners() {
    val transportClient = TransportClient(grpcServer.name)
    val eventLatch = CountDownLatch(3)
    val waitLatch = CountDownLatch(1)
    val transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250))
    val expectedEvents = ArrayList<Common.Event>()

    // First event exists before listener is registered
    val echoEvent = Common.Event.newBuilder()
      .setTimestamp(0)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(123)
      .build()
    expectedEvents.add(echoEvent)
    transportService.addEventToEventGroup(FakeTransportService.FAKE_DEVICE_ID, echoEvent)

    // Create listener for ECHO event
    val echoListener = TransportEventListener(eventKind = Common.Event.Kind.ECHO,
                                              callback = { event ->
                                                assertThat(event).isEqualTo(expectedEvents.removeAt(0))
                                                waitLatch.countDown()
                                                eventLatch.countDown()
                                              }, executor = MoreExecutors.directExecutor())
    transportEventPoller.registerListener(echoListener)

    // Wait for the first event to be received
    try {
      assertThat(waitLatch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

    // Second event created after first is received
    val echoEvent2 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(456)
      .build()
    expectedEvents.add(echoEvent2)
    transportService.addEventToEventGroup(FakeTransportService.FAKE_DEVICE_ID, echoEvent2)

    // Third event with the same group ID
    val echoEvent3 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(456)
      .build()
    expectedEvents.add(echoEvent3)
    transportService.addEventToEventGroup(FakeTransportService.FAKE_DEVICE_ID, echoEvent3)

    // Receive the last 2 events
    try {
      assertThat(eventLatch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

  }
}
