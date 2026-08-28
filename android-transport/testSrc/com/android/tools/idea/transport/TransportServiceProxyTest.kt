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
package com.android.tools.idea.transport

import com.android.SdkConstants
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.ProfileableClient
import com.android.ddmlib.ProfileableClientData
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder
import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportServiceProxy.Companion.PRE_LOLLIPOP_FAILURE_REASON
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command.CommandType.BEGIN_SESSION
import com.android.tools.profiler.proto.Commands.Command.CommandType.ECHO
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.Transport.TimeRequest
import com.android.tools.profiler.proto.Transport.TimeResponse
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.matches
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TransportServiceProxyTest {
  @get:Rule val myTimeout = Timeout.seconds(60)

  @Test
  fun testBindServiceContainsAllMethods() {
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, emptyArray())
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        startNamedChannel("testBindServiceContainsAllMethods", FakeTransportService()),
        LinkedBlockingDeque(),
        mutableMapOf(),
      )
    val serviceDefinition = proxy.serviceDefinition
    val allMethods = TransportServiceGrpc.getServiceDescriptor().methods
    val definedMethods = serviceDefinition.methods.map { it.methodDescriptor }.toSet()
    assertThat(definedMethods).hasSize(allMethods.size)
    assertThat(definedMethods).containsExactlyElementsIn(allMethods)
  }

  @Test
  fun testUnknownDeviceLabel() {
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, emptyArray())
    val profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)

    assertThat(profilerDevice.model).isEqualTo("Unknown")
  }

  @Test
  fun testUnsupportedReason() {
    val mockDevice1 = createMockDevice(AndroidVersion.VersionCodes.KITKAT, emptyArray())
    var profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice1)
    assertThat(profilerDevice.unsupportedReason).isEqualTo(PRE_LOLLIPOP_FAILURE_REASON)

    val mockDevice2 = createMockDevice(AndroidVersion.VersionCodes.Q, emptyArray())
    profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice2)
    assertThat(profilerDevice.unsupportedReason).isEmpty()
  }

  @Test
  fun testUnknownEmulatorLabel() {
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, emptyArray())
    whenever(mockDevice.isEmulator).thenReturn(true)
    whenever(mockDevice.avdName).thenReturn(null)
    val profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)

    assertThat(profilerDevice.model).isEqualTo("Serial")
  }

  @Test
  fun testClientsWithNullDescriptionsNotCached() {
    val client1 = createMockClient(1, "test1", "testClientDescription")
    val client2 = createMockClient(2, "test2", null)
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, arrayOf(client1, client2))
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)

    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        startNamedChannel("testClientsWithNullDescriptionsNotCached", FakeTransportService()),
        LinkedBlockingDeque(),
        mutableMapOf(),
      )
    val cachedProcesses = proxy.cachedProcesses
    assertThat(cachedProcesses).hasSize(1)
    val cachedProcess = cachedProcesses.entries.first()
    assertThat(cachedProcess.key).isEqualTo(client1.clientData.pid)
    assertThat(cachedProcess.value.pid).isEqualTo(1)
    assertThat(cachedProcess.value.packageName).isEqualTo("test1")
    assertThat(cachedProcess.value.name).isEqualTo("testClientDescription")
    assertThat(cachedProcess.value.state).isEqualTo(Common.Process.State.ALIVE)
    assertThat(cachedProcess.value.abiCpuArch).isEqualTo(SdkConstants.CPU_ARCH_ARM)
  }

  @Test
  fun profileableClientsAlsoCached() {
    val client1 = createMockClient(1, "test1", "name1")
    val client2 = createMockProfileableClient(2, "name2")
    val device =
      createMockDevice(
        AndroidVersion.VersionCodes.S,
        arrayOf(client1),
        arrayOf(client2),
      )
    val transportDevice = TransportServiceProxy.transportDeviceFromIDevice(device)

    val proxy =
      TransportServiceProxy(
        device,
        transportDevice,
        startNamedChannel("profileableClientsAlsoCached", FakeTransportService()),
        LinkedBlockingDeque(),
        mutableMapOf(),
      )
    val cachedProcesses = proxy.cachedProcesses
    assertThat(cachedProcesses).hasSize(2)
    val process1 = cachedProcesses.getValue(1)
    assertThat(process1.pid).isEqualTo(1)
    assertThat(process1.packageName).isEqualTo("test1")
    assertThat(process1.name).isEqualTo("name1")
    assertThat(process1.state).isEqualTo(Common.Process.State.ALIVE)
    assertThat(process1.abiCpuArch).isEqualTo(SdkConstants.CPU_ARCH_ARM)
    assertThat(process1.exposureLevel).isEqualTo(Common.Process.ExposureLevel.DEBUGGABLE)

    val process2 = cachedProcesses.getValue(2)
    assertThat(process2.pid).isEqualTo(2)
    assertThat(process2.name).isEqualTo("name2")
    assertThat(process2.state).isEqualTo(Common.Process.State.ALIVE)
    assertThat(process2.abiCpuArch).isEqualTo(SdkConstants.CPU_ARCH_ARM)
    assertThat(process2.exposureLevel).isEqualTo(Common.Process.ExposureLevel.PROFILEABLE)
  }

  @Test
  fun testEventStreaming() {
    val client1 = createMockClient(1, "test1", "testClientDescription")
    val client2 = createMockClient(2, "test2", "testClientDescription")
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, arrayOf(client1, client2))
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val thruService = FakeTransportService()
    val thruChannel = startNamedChannel("testEventStreaming", thruService)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        thruChannel,
        LinkedBlockingDeque(),
        mutableMapOf(),
      )

    val receivedEvents = ArrayList<Common.Event>()
    // We should expect six events: two process starts events, followed by event1 and event2, then process ends events.
    val latch = CountDownLatch(1)
    proxy.getEvents(
      Transport.GetEventsRequest.getDefaultInstance(),
      object : StreamObserver<Common.Event> {
        override fun onNext(event: Common.Event) {
          receivedEvents.add(event)
        }

        override fun onError(throwable: Throwable) {
          fail("Should not throw: $throwable")
        }

        override fun onCompleted() {
          latch.countDown()
        }
      },
    )

    val event1 = Common.Event.newBuilder().setPid(1).setIsEnded(true).build()
    val endedGroupEvent2 = Common.Event.newBuilder().setKind(Common.Event.Kind.ECHO).setPid(2).setGroupId(2).setIsEnded(true).build()
    val openedGroupEvent3 = Common.Event.newBuilder().setKind(Common.Event.Kind.ECHO).setPid(3).setGroupId(3).build()
    thruService.addEvents(event1, endedGroupEvent2, openedGroupEvent3)
    thruService.stopEventThread()
    thruChannel.shutdownNow()
    proxy.disconnect()
    latch.await()

    assertThat(receivedEvents).hasSize(8)
    // We know event 1, endedGroupEvent2 and openedGroupEvent3 will arrive in order. But the two processes' events can arrive out of order.
    // So here we only check whether those events are somewhere in the returned list.
    assertThat(receivedEvents.count { it.process.processStarted.process.pid == 1 }).isEqualTo(1)
    assertThat(receivedEvents.count { it.process.processStarted.process.pid == 2 }).isEqualTo(1)
    assertThat(receivedEvents[2]).isEqualTo(event1)
    assertThat(receivedEvents[3]).isEqualTo(endedGroupEvent2)
    assertThat(receivedEvents[4]).isEqualTo(openedGroupEvent3)
    // Make sure we only receive end events for those that have group id set and are still not ended.
    assertThat(receivedEvents.count { it.kind == Common.Event.Kind.ECHO && it.groupId == 3L && it.isEnded }).isEqualTo(1)
    assertThat(receivedEvents.count { it.kind == Common.Event.Kind.PROCESS && it.groupId == 1L && it.pid == 1 && it.isEnded }).isEqualTo(1)
    assertThat(receivedEvents.count { it.kind == Common.Event.Kind.PROCESS && it.groupId == 2L && it.pid == 2 && it.isEnded }).isEqualTo(1)
  }

  @Test
  fun testProxyCommandHandlers() {
    val client = createMockClient(1, "test", "testClientDescription")
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, arrayOf(client))
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val thruService = FakeTransportService()
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        startNamedChannel("testProxyCommandHandlers", thruService),
        LinkedBlockingDeque(),
        mutableMapOf(),
      )

    val latch = CountDownLatch(1)
    proxy.registerCommandHandler(ECHO) {
      latch.countDown()
      Transport.ExecuteResponse.getDefaultInstance()
    }

    val observer = mock<StreamObserver<Transport.ExecuteResponse>>()
    proxy.execute(
      Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder().setType(BEGIN_SESSION)).build(),
      observer,
    )
    assertThat(thruService.myLastCommandType).isEqualTo(BEGIN_SESSION)

    proxy.execute(
      Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder().setType(ECHO)).build(),
      observer,
    )
    try {
      latch.await()
    } catch (ignored: InterruptedException) {}
    // Last command will not be ECHO, as it was consumed by the proxy.
    assertThat(thruService.myLastCommandType).isEqualTo(BEGIN_SESSION)
  }

  @Test
  fun testProxyEventPreprocessors() {
    val client = createMockClient(1, "test", "testClientDescription")
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, arrayOf(client))
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val thruService = FakeTransportService()
    val thruChannel = startNamedChannel("testEventPreprocessors", thruService)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        thruChannel,
        LinkedBlockingDeque(),
        mutableMapOf(),
      )

    val latch = CountDownLatch(1)
    val receivedEvents = ArrayList<Common.Event>()
    val preprocessedEvents = ArrayList<Common.Event>()
    val generatedEvent = Common.Event.getDefaultInstance()
    proxy.registerEventPreprocessor(
      object : TransportEventPreprocessor {
        override fun shouldPreprocess(event: Common.Event): Boolean {
          return event.kind == Common.Event.Kind.ECHO
        }

        override fun preprocessEvent(event: Common.Event): Iterable<Common.Event> {
          preprocessedEvents.add(event)
          latch.countDown()
          return listOf(generatedEvent)
        }
      }
    )
    proxy.getEvents(
      Transport.GetEventsRequest.getDefaultInstance(),
      object : StreamObserver<Common.Event> {
        override fun onNext(event: Common.Event) {
          receivedEvents.add(event)
        }

        override fun onError(throwable: Throwable) {
          fail("Should not throw: $throwable")
        }

        override fun onCompleted() {}
      },
    )
    val eventToPreprocess = Common.Event.newBuilder().setPid(1).setKind(Common.Event.Kind.ECHO).setIsEnded(true).build()
    val eventToIgnore = Common.Event.newBuilder().setPid(1).setIsEnded(true).build()
    // Add eventToIgnore before eventToPreprocess because the latch-based synchronization counts on eventToPreprocess.
    // If eventToIgnore is added after, the service may stop before the event is sent, or the assertion is checked before
    // the event is received or recorded.
    thruService.addEvents(eventToIgnore, eventToPreprocess)
    thruService.stopEventThread()
    thruChannel.shutdownNow()
    proxy.disconnect()
    latch.await()
    assertThat(receivedEvents).containsAllOf(eventToPreprocess, eventToIgnore, generatedEvent)
    assertThat(preprocessedEvents).containsExactly(eventToPreprocess)
  }

  @Test
  fun testProxyDataPreprocessor() {
    // Setup
    val client = createMockClient(1, "test", "testClientDescription")
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, arrayOf(client))
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val thruService = FakeTransportService()
    val thruChannel = startNamedChannel("testProxyDataPreprocessor", thruService)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        thruChannel,
        LinkedBlockingDeque(),
        mutableMapOf(),
      )
    // Fake Data Preprocessor.
    val receivedData = ArrayList<String>()
    val preprocessor =
      object : TransportBytesPreprocessor {
        override fun shouldPreprocess(request: Transport.BytesRequest): Boolean {
          return request.id == "1"
        }

        override fun preprocessBytes(id: String, event: ByteString): ByteString {
          return ByteString.copyFromUtf8("WORLD")
        }
      }
    proxy.registerDataPreprocessor(preprocessor)

    // Handle returning data to proxy service.
    val request = Transport.BytesRequest.newBuilder()
    val validation =
      object : StreamObserver<Transport.FileResponse> {
        override fun onNext(response: Transport.FileResponse) {
          receivedData.add(response.filePath)
        }

        override fun onError(throwable: Throwable) {
          fail("Should not throw: $throwable")
        }

        override fun onCompleted() {}
      }

    // Run test.
    proxy.getFile(request.build(), validation)
    request.setId("1")
    proxy.getFile(request.build(), validation)
    // Clean up
    thruService.stopEventThread()
    thruChannel.shutdownNow()
    proxy.disconnect()
    // Validate
    // For both assertions, we need to read the content from the returned file path and compare it to the expected bytes.
    val content1 = ByteString.copyFrom(Files.readAllBytes(File(receivedData[0]).toPath()))
    assertThat(content1).isEqualTo(FakeTransportService.TEST_BYTES)
    val content2 = ByteString.copyFrom(Files.readAllBytes(File(receivedData[1]).toPath()))
    assertThat(content2).isEqualTo(preprocessor.preprocessBytes("1", FakeTransportService.TEST_BYTES))
  }

  @Test
  fun testProxyDataPreprocessor_emptyFileErrorFromDevice() {
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, emptyArray())
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val thruService =
      object : FakeTransportService() {
        override fun getBytesInChunks(request: Transport.BytesRequest, responseObserver: StreamObserver<Transport.BytesInChunksResponse>) {
          responseObserver.onError(Status.NOT_FOUND.withDescription("File is empty").asRuntimeException())
        }
      }
    val thruChannel = startNamedChannel("testProxyDataPreprocessor_emptyFileErrorFromDevice", thruService)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        thruChannel,
        LinkedBlockingDeque(),
        mutableMapOf(),
      )
    val preprocessor =
      object : TransportBytesPreprocessor {
        override fun shouldPreprocess(request: Transport.BytesRequest): Boolean {
          return true
        }

        override fun preprocessBytes(id: String, event: ByteString): ByteString {
          return event
        }
      }
    proxy.registerDataPreprocessor(preprocessor)

    val request = Transport.BytesRequest.newBuilder().setId("1")
    val receivedData = ArrayList<String>()
    val validation =
      object : StreamObserver<Transport.FileResponse> {
        override fun onNext(response: Transport.FileResponse) {
          receivedData.add(response.filePath)
        }

        override fun onError(throwable: Throwable) {
          fail("Should not throw on empty file error: $throwable")
        }

        override fun onCompleted() {}
      }

    proxy.getFile(request.build(), validation)
    thruService.stopEventThread()
    thruChannel.shutdownNow()
    proxy.disconnect()

    assertThat(receivedData).hasSize(1)
    assertThat(receivedData[0]).isEmpty()
  }

  @Test
  fun testProxyNoPreprocessor_emptyFileErrorFromDevice() {
    val mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, emptyArray())
    val transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val thruService =
      object : FakeTransportService() {
        override fun getBytesInChunks(request: Transport.BytesRequest, responseObserver: StreamObserver<Transport.BytesInChunksResponse>) {
          responseObserver.onError(Status.NOT_FOUND.withDescription("File is empty").asRuntimeException())
        }
      }
    val thruChannel = startNamedChannel("testProxyNoPreprocessor_emptyFileErrorFromDevice", thruService)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportMockDevice,
        thruChannel,
        LinkedBlockingDeque(),
        mutableMapOf(),
      )

    val request = Transport.BytesRequest.newBuilder().setId("1")
    val receivedData = ArrayList<String>()
    val validation =
      object : StreamObserver<Transport.FileResponse> {
        override fun onNext(response: Transport.FileResponse) {
          receivedData.add(response.filePath)
        }

        override fun onError(throwable: Throwable) {
          fail("Should not throw on empty file error: $throwable")
        }

        override fun onCompleted() {}
      }

    proxy.getFile(request.build(), validation)
    thruService.stopEventThread()
    thruChannel.shutdownNow()
    proxy.disconnect()

    assertThat(receivedData).hasSize(1)
    assertThat(receivedData[0]).isEmpty()
  }

  @Test
  fun bootIdIsSetCorrectly() {
    val client1 = createMockClient(1, "test1", "name1")
    val client2 = createMockProfileableClient(2, "name2")
    val device = createMockDevice(AndroidVersion.VersionCodes.S, arrayOf(client1), arrayOf(client2))
    val transportDevice = TransportServiceProxy.transportDeviceFromIDevice(device)
    assertThat(transportDevice.bootId).isEqualTo("boot-id")
  }

  @Test
  fun testArtVersionCodeRetrievedFromDevice() {
    val mockDevice = createMockDevice(37, emptyArray())
    doAnswer { invocation ->
        val args = invocation.arguments
        val bytes = "package:com.google.android.art versionCode:373399999\n".toByteArray(StandardCharsets.UTF_8)
        (args[1] as IShellOutputReceiver).addOutput(bytes, 0, bytes.size)
        (args[1] as IShellOutputReceiver).flush()
        null
      }
      .whenever(mockDevice)
      .executeShellCommand(matches(".*com\\.google\\.android\\.art.*"), any(), anyLong(), any())

    val profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    assertThat(profilerDevice.artVersionCode).isEqualTo(373399999L)
  }

  @Test
  fun testArtVersionCodeDefaultsToZeroOnOlderDevices() {
    val mockDevice = createMockDevice(30, emptyArray()) // API < 31 (S)
    val profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    assertThat(profilerDevice.artVersionCode).isEqualTo(0L)
  }

  @Test
  fun testUidRetrievedFromDevice() {
    val mockDevice = createMockDevice(30, emptyArray())
    val client1 = createMockClient(1, "test1", "name1")
    doAnswer { invocation ->
        val args = invocation.arguments
        val command = args[0] as String
        if (command == "cat /proc/1/status") {
          val bytes = "Uid:\t30005\t30005\t30005\t30005\n".toByteArray(StandardCharsets.UTF_8)
          (args[1] as IShellOutputReceiver).addOutput(bytes, 0, bytes.size)
          (args[1] as IShellOutputReceiver).flush()
        }
        null
      }
      .whenever(mockDevice)
      .executeShellCommand(startsWith("cat /proc/"), any(), anyLong(), any())

    whenever(mockDevice.clients).thenReturn(arrayOf(client1))
    val transportDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice)
    val proxy =
      TransportServiceProxy(
        mockDevice,
        transportDevice,
        startNamedChannel("testUidRetrievedFromDevice", FakeTransportService()),
        LinkedBlockingDeque(),
        mutableMapOf(),
      )
    val cachedProcesses = proxy.cachedProcesses
    val process1 = cachedProcesses.getValue(1)
    assertThat(process1.uid).isEqualTo(30005)
  }

  /** @param uniqueName Name should be unique across tests. */
  private fun startNamedChannel(uniqueName: String, thruService: FakeTransportService): ManagedChannel {
    val builder = InProcessServerBuilder.forName(uniqueName)
    builder.addService(thruService)
    val server = builder.build()
    server.start()

    return InProcessChannelBuilder.forName(uniqueName).build()
  }

  private fun createMockDevice(
    version: Int,
    clients: Array<Client>,
    profileables: Array<ProfileableClient> = emptyArray(),
  ): IDevice {
    val allProfileables =
      if (version >= AndroidVersion.VersionCodes.S) {
        (clients.map { createMockProfileableClient(it) } + profileables.toList()).toTypedArray()
      } else {
        emptyArray()
      }
    return mock<IDevice> {
      whenever(it.serialNumber).thenReturn("Serial")
      whenever(it.name).thenReturn("Device")
      whenever(it.version).thenReturn(AndroidVersion(version, null))
      whenever(it.isOnline).thenReturn(true)
      whenever(it.clients).thenReturn(clients)
      whenever(it.profileableClients).thenReturn(allProfileables)
      whenever(it.state).thenReturn(IDevice.DeviceState.ONLINE)
      whenever(it.abis).thenReturn(listOf("armeabi"))
      whenever(it.getProperty(IDevice.PROP_BUILD_TAGS)).thenReturn("release-keys")
      whenever(it.getProperty(IDevice.PROP_BUILD_TYPE)).thenReturn("user")
      whenever(it.getProperty(IDevice.PROP_DEVICE_CPU_ABI)).thenReturn("armeabi")
      doAnswer { invocation ->
          val args = invocation.arguments
          (args[1] as IShellOutputReceiver).addOutput("boot-id\n".toByteArray(), 0, 8)
          (args[1] as IShellOutputReceiver).flush()
          null
        }
        .whenever(it)
        .executeShellCommand(any(), any())
    }
  }

  private fun createMockClient(pid: Int, packageName: String?, processName: String?): Client {
    val mockData =
      mock<ClientData> {
        whenever(it.pid).thenReturn(pid)
        whenever(it.packageName).thenReturn(packageName)
        whenever(it.processName).thenReturn(processName)
      }
    return mock<Client> {
      whenever(it.clientData).thenReturn(mockData)
    }
  }

  private fun createMockProfileableClient(pid: Int, processName: String?): ProfileableClient {
    val mockData =
      mock<ProfileableClientData> {
        whenever(it.pid).thenReturn(pid)
        whenever(it.processName).thenReturn(processName)
      }
    return mock<ProfileableClient> {
      whenever(it.profileableClientData).thenReturn(mockData)
    }
  }

  private fun createMockProfileableClient(client: Client): ProfileableClient {
    return createMockProfileableClient(client.clientData.pid, client.clientData.processName)
  }

  private open class FakeTransportService : TransportServiceGrpc.TransportServiceImplBase() {
    private val myEventQueue = LinkedBlockingDeque<Common.Event>()
    private var myEventThread: Thread? = null
    var myLastCommandType: Commands.Command.CommandType? = null

    override fun getCurrentTime(request: TimeRequest, responseObserver: StreamObserver<TimeResponse>) {
      responseObserver.onNext(TimeResponse.getDefaultInstance())
      responseObserver.onCompleted()
    }

    override fun getEvents(request: Transport.GetEventsRequest, responseObserver: StreamObserver<Common.Event>) {
      myEventThread = Thread {
        while (!Thread.currentThread().isInterrupted || !myEventQueue.isEmpty()) {
          try {
            val event = myEventQueue.take()
            if (event != null) {
              responseObserver.onNext(event)
            }
          } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
          }
        }
        responseObserver.onCompleted()
      }
      myEventThread!!.start()
    }

    override fun getFile(request: Transport.BytesRequest, responseObserver: StreamObserver<Transport.FileResponse>) {
      try {
        val tempFile = TransportServiceUtils.createTempFile("transport", ".dat", TEST_BYTES)
        responseObserver.onNext(Transport.FileResponse.newBuilder().setFilePath(tempFile.absolutePath).build())
        responseObserver.onCompleted()
      } catch (e: IOException) {
        responseObserver.onError(e)
      }
    }

    override fun getBytesInChunks(request: Transport.BytesRequest, responseObserver: StreamObserver<Transport.BytesInChunksResponse>) {
      responseObserver.onNext(Transport.BytesInChunksResponse.newBuilder().setChunk(TEST_BYTES).build())
      responseObserver.onCompleted()
    }

    override fun execute(request: Transport.ExecuteRequest, responseObserver: StreamObserver<Transport.ExecuteResponse>) {
      myLastCommandType = request.command.type
      responseObserver.onNext(Transport.ExecuteResponse.getDefaultInstance())
      responseObserver.onCompleted()
    }

    fun addEvents(vararg events: Common.Event) {
      for (event in events) {
        myEventQueue.offer(event)
      }
      while (!myEventQueue.isEmpty()) {
        try {
          Thread.sleep(10)
        } catch (ignored: InterruptedException) {}
      }
    }

    fun stopEventThread() {
      myEventThread?.interrupt()
    }

    companion object {
      val TEST_BYTES: ByteString = ByteString.copyFromUtf8("Hello")
    }
  }
}
