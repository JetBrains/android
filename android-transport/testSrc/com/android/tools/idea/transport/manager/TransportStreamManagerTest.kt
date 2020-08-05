/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.transport.manager

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TransportStreamManagerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)
  private val fakeDevice2 = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(FakeTransportService.FAKE_DEVICE_ID + 1).build()
  private val offlineFakeDevice2 =
    FakeTransportService.FAKE_OFFLINE_DEVICE.toBuilder().setDeviceId(FakeTransportService.FAKE_DEVICE_ID + 1).build()
  private val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(fakeDevice2.deviceId).build()

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService, transportService)!!

  @get:Rule
  val timeoutRule = Timeout(5, TimeUnit.SECONDS)

  @Test
  fun discoverNewStream() {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, TimeUnit.MILLISECONDS.toNanos(100))

    val streamReadyLatch = CountDownLatch(1)
    val streamDeadLatch = CountDownLatch(1)
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        assertThat(streamChannel.stream.device).isEqualTo(FakeTransportService.FAKE_DEVICE)
        streamReadyLatch.countDown()
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
        streamDeadLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyLatch.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)

    streamDeadLatch.await()
  }

  @Test
  fun rediscoverStream() {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, TimeUnit.MILLISECONDS.toNanos(100))

    val streamReadyLatch = CountDownLatch(1)
    val streamReadyAgainLatch = CountDownLatch(1)
    val streamDeadLatch = CountDownLatch(1)
    manager.addStreamListener(object : TransportStreamListener {
      private var calledCounter = 0
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        if (calledCounter == 0) {
          calledCounter++
          streamReadyLatch.countDown()
        } else {
          streamReadyAgainLatch.countDown()
        }
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
        streamDeadLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyLatch.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)

    streamDeadLatch.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyAgainLatch.await()
  }

  @Test
  fun discoverMultipleStreams() {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, TimeUnit.MILLISECONDS.toNanos(100))

    val streamReadyLatch = CountDownLatch(2)
    val streamDeadLatch = CountDownLatch(2)
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        streamReadyLatch.countDown()
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
        streamDeadLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    timer.currentTimeNs += 1
    transportService.addDevice(fakeDevice2)
    timer.currentTimeNs += 1

    streamReadyLatch.await()

    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)
    timer.currentTimeNs += 1
    transportService.addDevice(offlineFakeDevice2)
    timer.currentTimeNs += 1

    streamDeadLatch.await()
  }

  @Test
  fun registerNewListener() {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, TimeUnit.MILLISECONDS.toNanos(100))

    val streamReadyLatch = CountDownLatch(1)
    var stream: TransportStreamChannel? = null
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        stream = streamChannel
        streamReadyLatch.countDown()
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
      }
    }, MoreExecutors.directExecutor())

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyLatch.await()

    val eventHeardLatch = CountDownLatch(1)
    stream!!.registerStreamEventListener(
      TransportStreamEventListener(
        Common.Event.Kind.PROCESS,
        executor = MoreExecutors.directExecutor()
      ) {
        eventHeardLatch.countDown()
      }
    )

    transportService.addEventToStream(
      stream!!.stream.streamId,
      Common.Event.newBuilder()
        .setKind(Common.Event.Kind.PROCESS)
        .setPid(1)
        .build()
    )

    eventHeardLatch.await()
  }

  // Tests stream manager does not ignore stream events from a device that has a slower clock.
  @Test
  fun streamsWithDifferentClocks() {
    val manager =
      TransportStreamManager.createManager(TransportClient(grpcServerRule.name).transportStub, TimeUnit.MILLISECONDS.toNanos(100))

    val streamReadyLatch = CountDownLatch(2)
    val processLatch = CountDownLatch(2)
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(Common.Event.Kind.PROCESS, executor =  MoreExecutors.directExecutor()) {
            processLatch.countDown()
          }
        )
        streamReadyLatch.countDown()
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
      }
    }, MoreExecutors.directExecutor())

    // Start stream 1 and stream 2
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addDevice(fakeDevice2)

    // Wait for streams to be ready
    streamReadyLatch.await()

    // Add process 1 to stream 1
    timer.currentTimeNs += 1
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    // Add process 2 with an earlier timestamp than process 1 to stream 2
    timer.currentTimeNs -= 1
    transportService.addProcess(fakeDevice2, fakeProcess2)

    // Await to see if both processes are picked up
    processLatch.await()
  }
}