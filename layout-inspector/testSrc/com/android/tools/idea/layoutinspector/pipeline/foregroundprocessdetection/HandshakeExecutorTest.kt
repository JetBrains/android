/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.transport.TransportClient
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.ExecuteRequest
import com.android.tools.profiler.proto.Transport.ExecuteResponse
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAutoConnectInfo
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import layout_inspector.LayoutInspector
import layout_inspector.LayoutInspector.TrackingForegroundProcessSupported.SupportType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import kotlin.test.fail

class HandshakeExecutorTest {

  @get:Rule
  val projectRule = ProjectRule()

  private val pollingIntervalMs = 200L

  private val deviceDescriptor = object : DeviceDescriptor {
    override val manufacturer = "manufacturer"
    override val model = "mod"
    override val serial = "serial"
    override val isEmulator = false
    override val apiLevel = 0
    override val version = "version"
    override val codename = "codename"
  }

  private val stream = createFakeStream(1, deviceDescriptor)

  private lateinit var scope: CoroutineScope
  private lateinit var workDispatcher: CoroutineDispatcher
  private lateinit var mockClient: TransportClient
  private val mockMetrics = mock<ForegroundProcessDetectionMetrics>()

  // channel used to wait for events to actually being sent
  private lateinit var syncChannel: Channel<ExecuteRequest>

  @Before
  fun setUp() {
    syncChannel = Channel()

    scope = projectRule.project.coroutineScope
    workDispatcher = AndroidDispatchers.workerThread
    mockClient = mock()
    val mockStub = mock<TransportServiceBlockingStub>()
    `when`(mockClient.transportStub).thenReturn(mockStub)
    `when`(mockStub.execute(any())).then {
      assertThat(it.arguments.size).isEqualTo(1)
      assertThat(it.arguments.first()).isInstanceOf(ExecuteRequest::class.java)
      runBlocking { syncChannel.send(it.arguments.first() as ExecuteRequest) }
      return@then ExecuteResponse.newBuilder().build()
    }
  }

  @After
  fun tearDown() {
    syncChannel.close()
  }

  @Test
  fun testDeviceConnectedInitiatesHandshake() {
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val receivedRequest = syncChannel.receive()
      verifyNoMoreRequests()

      val expectedRequest = createHandshakeExecuteRequest(1)
      assertThat(receivedRequest).isEqualTo(expectedRequest)
    }

    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testSupportedStopsPolling() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.Supported(createTrackingForegroundProcessSupportedEvent(SupportType.SUPPORTED)))
      verifyNoMoreRequests()
    }

    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.SUPPORTED), deviceDescriptor)
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testConnectedDisconnectedStopsPolling() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.Disconnected)
      verifyNoMoreRequests()
    }

    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testNotSupportedStopsPolling() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.NotSupported(createTrackingForegroundProcessSupportedEvent(SupportType.NOT_SUPPORTED)))
      verifyNoMoreRequests()
    }

    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.NOT_SUPPORTED), deviceDescriptor)
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testMultipleConnectedEventsInitiatesHandshakeOnlyOnce() {
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      syncChannel.receive()

      handshakeExecutor.post(HandshakeState.Connected)
      val receivedRequest = withTimeoutOrNull(pollingIntervalMs*2) {
        syncChannel.receive()
      }

      assertThat(receivedRequest).isNull()
    }

    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testUnknownSupportedInitiatesHandshakePeriodically() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest2 = syncChannel.receive()
      assertThat(executeRequest2).isEqualTo(expectedRequest)

      val executeRequest3 = syncChannel.receive()
      assertThat(executeRequest3).isEqualTo(expectedRequest)

      val executeRequest4 = syncChannel.receive()
      assertThat(executeRequest4).isEqualTo(expectedRequest)
    }

    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN), deviceDescriptor)
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testUnknownToSupportedStopsPolling() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest2 = syncChannel.receive()
      assertThat(executeRequest2).isEqualTo(expectedRequest)

      val executeRequest3 = syncChannel.receive()
      assertThat(executeRequest3).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.Supported(createTrackingForegroundProcessSupportedEvent(SupportType.SUPPORTED)))
      verifyNoMoreRequests()
    }

    // unknown
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN), deviceDescriptor)
    // supported
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.SUPPORTED), deviceDescriptor)
    verify(mockMetrics).logHandshakeConversion(
      DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_SUPPORTED, deviceDescriptor
    )
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testUnknownToNotSupportedStopsPolling() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest2 = syncChannel.receive()
      assertThat(executeRequest2).isEqualTo(expectedRequest)

      val executeRequest3 = syncChannel.receive()
      assertThat(executeRequest3).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.NotSupported(createTrackingForegroundProcessSupportedEvent(SupportType.NOT_SUPPORTED)))
      verifyNoMoreRequests()
    }

    // unknown
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN), deviceDescriptor)
    // not supported
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.NOT_SUPPORTED), deviceDescriptor)
    verify(mockMetrics).logHandshakeConversion(
      DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_NOT_SUPPORTED, deviceDescriptor
    )
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testUnknownToDisconnectedStopsPolling() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest2 = syncChannel.receive()
      assertThat(executeRequest2).isEqualTo(expectedRequest)

      val executeRequest3 = syncChannel.receive()
      assertThat(executeRequest3).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.Disconnected)
      verifyNoMoreRequests()
    }

    // unknown
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN), deviceDescriptor)
    // unknown not resolved
    verify(mockMetrics).logHandshakeConversion(
      DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_DISCONNECTED, deviceDescriptor
    )
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testSendingMultipleUnknownDevicesDoesntInitiateMultiplePollingSessions() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest2 = syncChannel.receive()
      assertThat(executeRequest2).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest3 = syncChannel.receive()
      assertThat(executeRequest3).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest4 = syncChannel.receive()
      assertThat(executeRequest4).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.Disconnected)
      verifyNoMoreRequests()
    }

    // unknown
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN), deviceDescriptor)
    // unknown not resolved
    verify(mockMetrics).logHandshakeConversion(
      DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_DISCONNECTED, deviceDescriptor
    )
    verifyNoMoreInteractions(mockMetrics)
  }

  @Test
  fun testNotSupportedToSupportedLogsMetrics() {
    val expectedRequest = createHandshakeExecuteRequest(1)
    val handshakeExecutor = HandshakeExecutor(deviceDescriptor, stream, scope, workDispatcher, mockClient, mockMetrics, pollingIntervalMs)
    runBlocking {
      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest1 = syncChannel.receive()
      assertThat(executeRequest1).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.UnknownSupported(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN)))
      val executeRequest2 = syncChannel.receive()
      assertThat(executeRequest2).isEqualTo(expectedRequest)

      val executeRequest3 = syncChannel.receive()
      assertThat(executeRequest3).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.NotSupported(createTrackingForegroundProcessSupportedEvent(SupportType.NOT_SUPPORTED)))

      handshakeExecutor.post(HandshakeState.Connected)
      val executeRequest4 = syncChannel.receive()
      assertThat(executeRequest4).isEqualTo(expectedRequest)

      handshakeExecutor.post(HandshakeState.Supported(createTrackingForegroundProcessSupportedEvent(SupportType.SUPPORTED)))
      verifyNoMoreRequests()
    }

    // unknown
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.UNKNOWN), deviceDescriptor)
    // unknown not resolved
    verify(mockMetrics).logHandshakeConversion(
      DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_NOT_SUPPORTED, deviceDescriptor
    )
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.NOT_SUPPORTED), deviceDescriptor)
    verify(mockMetrics).logHandshakeResult(createTrackingForegroundProcessSupportedEvent(SupportType.SUPPORTED), deviceDescriptor)
    verify(mockMetrics).logHandshakeConversion(
      DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_NOT_SUPPORTED_TO_SUPPORTED, deviceDescriptor
    )
    verifyNoMoreInteractions(mockMetrics)
  }

  private fun createTrackingForegroundProcessSupportedEvent(supportType: SupportType): LayoutInspector.TrackingForegroundProcessSupported {
    return LayoutInspector.TrackingForegroundProcessSupported.newBuilder().setSupportType(supportType).build()
  }

  /**
   * Verifies that no more events are sent on the sync channel
   */
  private suspend fun verifyNoMoreRequests() {
    withTimeoutOrNull<Nothing>(pollingIntervalMs*2) {
      syncChannel.receive()
      fail()
    }
  }

  @Suppress("SameParameterValue")
  private fun createHandshakeExecuteRequest(streamId: Long): ExecuteRequest {
    val command = Commands.Command
      .newBuilder()
      .setType(Commands.Command.CommandType.IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED)
      .setStreamId(streamId)
      .build()
    return ExecuteRequest.newBuilder().setCommand(command).build()
  }

  @Suppress("SameParameterValue")
  private fun createFakeStream(streamId: Long, device: DeviceDescriptor): Common.Stream {
    return Common.Stream.newBuilder()
      .setStreamId(streamId)
      .setDevice(device.toTransportDevice(streamId))
      .build()
  }

  private fun DeviceDescriptor.toTransportDevice(id: Long): Common.Device {
    return Common.Device.newBuilder()
      .setDeviceId(id)
      .setSerial(serial)
      .setApiLevel(apiLevel)
      .setFeatureLevel(apiLevel)
      .setModel(model)
      .setCpuAbi("arm64-v8a")
      .setState(Common.Device.State.ONLINE)
      .build()
  }
}