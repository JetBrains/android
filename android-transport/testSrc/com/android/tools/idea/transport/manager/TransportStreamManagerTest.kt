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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalCoroutinesApi
class TransportStreamManagerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)
  private val fakeDevice2 = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(FakeTransportService.FAKE_DEVICE_ID + 1).build()
  private val offlineFakeDevice2 =
    FakeTransportService.FAKE_OFFLINE_DEVICE.toBuilder().setDeviceId(FakeTransportService.FAKE_DEVICE_ID + 1).build()
  private val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(fakeDevice2.deviceId).build()
  private lateinit var executorService: ExecutorService
  private lateinit var dispatcher: CoroutineDispatcher

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService)

  @Before
  fun setUp() {
    executorService = Executors.newSingleThreadExecutor()
    dispatcher = executorService.asCoroutineDispatcher()
  }

  fun tearDown() {
    dispatcher.cancel()
    executorService.shutdownNow()
  }

  @Test
  fun discoverNewStream() = runBlocking {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, dispatcher)

    val streamReadyDeferred = CompletableDeferred<Unit>()
    val streamDeadDeferred = CompletableDeferred<Unit>()

    launch {
      manager.streamActivityFlow()
        .take(2)
        .collect {
          if (it is StreamConnected) {
            assertThat(it.streamChannel.stream.device).isEqualTo(FakeTransportService.FAKE_DEVICE)
            streamReadyDeferred.complete(Unit)
          }
          else if (it is StreamDisconnected) {
            streamDeadDeferred.complete(Unit)
          }
        }
    }

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyDeferred.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)

    streamDeadDeferred.await()
  }

  @Test
  fun rediscoverStream() = runBlocking {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, dispatcher)

    val streamReadyDeferred = CompletableDeferred<Unit>()
    val streamReadyAgainDeferred = CompletableDeferred<Unit>()
    val streamDeadDeferred = CompletableDeferred<Unit>()
    launch {
      manager.streamActivityFlow()
        .take(3)
        .collectIndexed { index, activity ->
          if (activity is StreamConnected) {
            if (index == 0) {
              streamReadyDeferred.complete(Unit)
            }
            else {
              streamReadyAgainDeferred.complete(Unit)
            }
          }
          else if (activity is StreamDisconnected) {
            streamDeadDeferred.complete(Unit)
          }
        }
    }

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyDeferred.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)

    streamDeadDeferred.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    streamReadyAgainDeferred.await()
  }

  @Test
  fun discoverMultipleStreams() = runBlocking {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, dispatcher)

    val devicesDetected = CompletableDeferred<Unit>()
    launch {
      manager.streamActivityFlow()
        .take(4)
        .collectIndexed { index, activity ->
          if (index < 2) {
            assertThat(activity).isInstanceOf(StreamConnected::class.java)
          }
          else {
            assertThat(activity).isInstanceOf(StreamDisconnected::class.java)
          }
          if (index == 1) {
            devicesDetected.complete(Unit)
          }
        }
    }

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    timer.currentTimeNs += 1
    transportService.addDevice(fakeDevice2)
    timer.currentTimeNs += 1

    devicesDetected.await()

    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)
    timer.currentTimeNs += 1
    transportService.addDevice(offlineFakeDevice2)
    timer.currentTimeNs += 1
  }

  // Tests stream manager does not ignore stream events from a device that has a slower clock.
  @Test
  fun streamsWithDifferentClocks() = runBlocking {
    val manager =
      TransportStreamManager.createManager(TransportClient(grpcServerRule.name).transportStub, dispatcher)

    launch {
      manager.streamActivityFlow()
        .take(2)
        .collect { activity ->
          launch {
            activity.streamChannel.eventFlow(StreamEventQuery(Common.Event.Kind.PROCESS))
              .take(1)
              .collect()
          }
        }
    }

    // Start stream 1 and stream 2
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addDevice(fakeDevice2)

    // Add process 1 to stream 1
    timer.currentTimeNs += 1
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    // Add process 2 with an earlier timestamp than process 1 to stream 2
    timer.currentTimeNs -= 1
    transportService.addProcess(fakeDevice2, fakeProcess2)
  }

  @Test
  fun streamDisconnect_closesFlows() = runBlocking {
    val manager =
      TransportStreamManager.createManager(TransportClient(grpcServerRule.name).transportStub, dispatcher)

    val streamReadyDeferred = CompletableDeferred<Unit>()
    launch {
      manager.streamActivityFlow()
        .take(2)
        .collect { activity ->
          if (activity is StreamConnected) {
            launch {
              activity.streamChannel.eventFlow(StreamEventQuery(Common.Event.Kind.PROCESS))
                .collect {
                  // Collection should be cancelled when stream is disconnected.
                }
            }
            streamReadyDeferred.complete(Unit)
          }
        }
    }

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    streamReadyDeferred.await()

    timer.currentTimeNs += 1
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)
  }

  @Test
  fun `add and remove processes`() = runBlocking {
    val manager =
      TransportStreamManager
        .createManager(TransportClient(grpcServerRule.name).transportStub, dispatcher)

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)

    val queryChannel = Channel<List<Common.Process>>(capacity = 1)

    manager.streamActivityFlow()
      .take(1)
      .collect { activity ->
        queryChannel.send(listOf(FakeTransportService.FAKE_PROCESS, FakeTransportService.FAKE_PROFILEABLE_PROCESS))
        activity.streamChannel.processesFlow({ _, _ -> true }) {
          queryChannel.receive()
        }
          .take(4)
          .collectIndexed { index, process ->
            when (index) {
              0 -> assertThat(process).isEqualTo(FakeTransportService.FAKE_PROCESS)
              1 -> {
                assertThat(process).isEqualTo(FakeTransportService.FAKE_PROFILEABLE_PROCESS)
                queryChannel.send(listOf(FakeTransportService.FAKE_OFFLINE_PROCESS))
              }

              2 -> assertThat(process).isEqualTo(
                FakeTransportService.FAKE_PROFILEABLE_PROCESS.toBuilder().setState(Common.Process.State.DEAD).build())

              3 -> assertThat(process).isEqualTo(FakeTransportService.FAKE_OFFLINE_PROCESS)
            }
          }
      }
  }
}