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

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.ProfileableClient
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.ddms.DevicePropertyUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportProxy.ProxyCommandHandler
import com.android.tools.profiler.proto.Commands.Command.CommandType
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Transport.*
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.MethodDescriptor
import com.android.tools.idea.io.grpc.ServerCallHandler
import com.android.tools.idea.io.grpc.ServerServiceDefinition
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.io.grpc.stub.ServerCalls
import com.android.tools.idea.io.grpc.stub.StreamObserver
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Collections
import java.util.HashMap
import java.util.Random
import java.util.concurrent.BlockingDeque
import java.util.concurrent.CountDownLatch
import kotlin.math.max

/**
 * A proxy TransportService on host that intercepts grpc requests from transport-database to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 *
 * @param ddmlibDevice    the [IDevice] for retrieving process information.
 * @param transportDevice the [Common.Device] corresponding to the device,
 * as generated via [.transportDeviceFromIDevice]
 * @param channel         the channel that is used for communicating with the device daemon.
 * @param proxyEventQueue event queue shared by the proxy layer.
 * @param proxyBytesCache byte cache shared by the proxy layer.
 */
class TransportServiceProxy(private val ddmlibDevice: IDevice,
                            private val transportDevice: Common.Device,
                            channel: ManagedChannel,
                            private val proxyEventQueue: BlockingDeque<Common.Event>,
                            private val proxyBytesCache: MutableMap<String, ByteString>)
  : ServiceProxy(TransportServiceGrpc.getServiceDescriptor()), IClientChangeListener, IDeviceChangeListener {
  private val serviceStub = TransportServiceGrpc.newBlockingStub(channel)
  @TestOnly val cachedProcesses: MutableMap<Int, Common.Process> = Collections.synchronizedMap(HashMap())
  // Unsupported device are expected to have the unsupportedReason field set.
  private val isDeviceApiSupported = transportDevice.unsupportedReason.isEmpty()
  private var eventsListenerThread: Thread? = null
  private val commandHandlers = mutableMapOf<CommandType, ProxyCommandHandler>()
  private val eventPreprocessors = mutableListOf<TransportEventPreprocessor>()
  private val dataPreprocessors = mutableListOf<TransportBytesPreprocessor>()

  // Cache the latest event timestamp we received from the daemon, which is used for closing all still-opened event groups when
  // the proxy lost connection with the device.
  private var latestEventTimestampNs = Long.MIN_VALUE
  private var eventStreamingLatch: CountDownLatch? = null

  init {
    log.info("ProfilerDevice created: $transportDevice")
    updateProcesses()
    AndroidDebugBridge.addDeviceChangeListener(this)
    AndroidDebugBridge.addClientChangeListener(this)
  }

  fun registerCommandHandler(commandType: CommandType, handler: ProxyCommandHandler) = commandHandlers.put(commandType, handler)

  /**
   * Registers an event preprocessor that preprocesses events in [.getEvents].
   */
  fun registerEventPreprocessor(eventPreprocessor: TransportEventPreprocessor) = eventPreprocessors.add(eventPreprocessor)

  /**
   * Registers an event preprocessor that preprocesses events in [.getEvents].
   */
  fun registerDataPreprocessor(dataPreprocessor: TransportBytesPreprocessor) = dataPreprocessors.add(dataPreprocessor)

  override fun disconnect() {
    AndroidDebugBridge.removeDeviceChangeListener(this)
    AndroidDebugBridge.removeClientChangeListener(this)
    eventStreamingLatch?.let {
      try {
        it.await()
      } catch (ignored: InterruptedException) { }
    }
  }

  private fun getVersion(request: VersionRequest, responseObserver: StreamObserver<VersionResponse>) =
    responseObserver.onLast(VersionResponse.newBuilder().setVersion(transportDevice.version).build())

  @VisibleForTesting
  fun getEvents(request: GetEventsRequest, responseObserver: StreamObserver<Common.Event>) {
    // Create a thread to receive the stream of events from perfd.
    // We push all events into an event queue, so any proxy generated events can also be added.
    startThread {
      try {
        serviceStub.getEvents(request).forEach { it?.let(proxyEventQueue::offer) } // If the device is disconnected, response is null
      } catch (ignored: StatusRuntimeException) { } // disconnect handle generally outside of the exception.
      eventsListenerThread?.let {
        it.interrupt()
        eventsListenerThread = null
      }
    }

    // This loop runs on a GRPC thread, it should not exit until the grpc is terminated killing the thread.
    eventStreamingLatch = CountDownLatch(1)
    eventsListenerThread = startThread {
      val ongoingEventGroups: EventGrouping = mutableMapOf()
      // The loop keeps running if the queue is not emptied, to make sure we pipe through all the existing
      // events that are already in the queue.
      while (!Thread.currentThread().isInterrupted || !proxyEventQueue.isEmpty()) {
        try {
          val event = proxyEventQueue.take()
          latestEventTimestampNs = max(latestEventTimestampNs, event.timestamp)

          // Run registered preprocessors.
          for (preprocessor in eventPreprocessors) {
            if (preprocessor.shouldPreprocess(event)) {
              preprocessor.preprocessEvent(event).forEach(responseObserver::onNext)
            }
          }

          // Update the event cache: remove an event group if it has ended, otherwise cache the latest opened event for that group.
          when {
            event.isEnded -> ongoingEventGroups.remove(event)
            event.groupId != 0L -> ongoingEventGroups.add(event)
          }
          responseObserver.onNext(event)
        } catch (exception: InterruptedException) {
          Thread.currentThread().interrupt()
        }
      }

      // Create a generic end event with the input kind and group id.
      // Note - We will revisit this logic if it turns out we need to insert domain-specific data with the end event.
      // For the most part, since the device stream is disconnected, we should not have to care.
      ongoingEventGroups.values.forEach { it.values.forEach { lastEvent -> responseObserver.onNext(generateEndEvent(lastEvent)) } }
      responseObserver.onCompleted()
      eventStreamingLatch!!.countDown()
    }
  }

  @VisibleForTesting
  fun getBytes(request: BytesRequest, responseObserver: StreamObserver<BytesResponse>) = synchronized(proxyBytesCache) {
    // Removes cache to save memory once it has been requested/cached by the datastore.
    val response = when (val cached = proxyBytesCache.remove(request.id)) {
      null -> serviceStub.getBytes(request).toBuilder()
      else -> BytesResponse.newBuilder().setContents(cached)
    }
    // Run registered preprocessors.
    response.contents = dataPreprocessors.fold(response.contents) { contents, preprocessor ->
      if (preprocessor.shouldPreprocess(request)) preprocessor.preprocessBytes(request.id, contents) else contents
    }
    responseObserver.onLast(response.build())
  }

  private fun generateEndEvent(previousEvent: Common.Event) = Common.Event.newBuilder()
    .setKind(previousEvent.kind)
    .setGroupId(previousEvent.groupId)
    .setPid(previousEvent.pid)
    .setTimestamp(latestEventTimestampNs + 1)
    .setIsEnded(true)
    .build()

  private fun getCurrentTime(request: TimeRequest, responseObserver: StreamObserver<TimeResponse>) {
    val response = when {
      // if device API is supported, use grpc to get the current time
      isDeviceApiSupported ->
        try {
          serviceStub.getCurrentTime(request)
        } catch (e: StatusRuntimeException) {
          responseObserver.onError(e)
          return
        }
      // otherwise, return a default (any) instance of TimeResponse
      else -> TimeResponse.getDefaultInstance()
    }
    responseObserver.onLast(response)
  }

  private fun getProcesses(request: GetProcessesRequest, responseObserver: StreamObserver<GetProcessesResponse>) =
    responseObserver.onLast(GetProcessesResponse.newBuilder().addAllProcess(cachedProcesses.values).build())

  @VisibleForTesting
  fun execute(request: ExecuteRequest, responseObserver: StreamObserver<ExecuteResponse>) = request.command.let { command ->
    responseObserver.onLast(commandHandlers[command.type]?.let {
      if (it.shouldHandle(command)) it.execute(command) else null
    } ?: serviceStub.execute(request))
  }

  override fun deviceConnected(device: IDevice) { } // Don't care

  override fun deviceDisconnected(device: IDevice) { } // Don't care

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    // This event can be triggered when a device goes offline. However, `update{Debuggables,Profileables}` expect
    // an online device, so just ignore the event in that case.
    if (device === this.ddmlibDevice) {
      if (IDevice.CHANGE_CLIENT_LIST in changeMask) updateDebuggables()
      if (IDevice.CHANGE_PROFILEABLE_CLIENT_LIST in changeMask && isProfileableSupported()) updateProfileables()
    }
  }

  private fun isProfileableSupported() =
    ddmlibDevice.version.featureLevel >= AndroidVersion.VersionCodes.S

  override fun clientChanged(client: Client, changeMask: Int) {
    if (Client.CHANGE_NAME in changeMask && client.device === ddmlibDevice && client.clientData.clientDescription != null) {
      updateProcesses(listOf(ClientSummary.of(client)!!), listOf(), ExposureLevel.DEBUGGABLE)
    }
  }

  override fun getServiceDefinition(): ServerServiceDefinition {
    infix fun<Req, Res> MethodDescriptor<Req, Res>.to(f: (Req, StreamObserver<Res>) -> Unit) = this to ServerCalls.asyncUnaryCall(f)
    // The map erases types, so use above helper to ensure each entry is well-typed
    val overrides = mapOf<MethodDescriptor<*, *>, ServerCallHandler<*, *>>(
      TransportServiceGrpc.getGetVersionMethod() to ::getVersion,
      TransportServiceGrpc.getGetProcessesMethod() to ::getProcesses,
      TransportServiceGrpc.getGetCurrentTimeMethod() to ::getCurrentTime,
      TransportServiceGrpc.getGetEventsMethod() to ::getEvents,
      TransportServiceGrpc.getGetBytesMethod() to ::getBytes,
      TransportServiceGrpc.getExecuteMethod() to ::execute
    )
    return generatePassThroughDefinitions(overrides, serviceStub)
  }

  private fun updateProfileables() = updateProcesses(IDevice::getProfileableClients, ClientSummary::of, ExposureLevel.PROFILEABLE)
  private fun updateDebuggables() = updateProcesses(IDevice::getClients, ClientSummary::of, ExposureLevel.DEBUGGABLE)

  private fun updateProcesses() {
    updateDebuggables()
    if (isProfileableSupported()) updateProfileables()
  }

  private fun<C> updateProcesses(getList: IDevice.() -> Array<C>, summarizeClient: (C) -> ClientSummary?, level: ExposureLevel) {
    if (isDeviceApiSupported) {
      val currentProcesses = ddmlibDevice.getList().mapNotNull(summarizeClient)
      val previousProcessIds = cachedProcesses.mapNotNullTo(mutableSetOf()) { (id, p) -> id.takeIf { p.exposureLevel >= level } }
      val addedProcesses = currentProcesses.filterNot { it.pid in previousProcessIds }
      val removedProcessIds = previousProcessIds - currentProcesses.map(ClientSummary::pid)

      // This is needed as:
      // 1) the service test calls this function without setting up a full profiler service, and
      // 2) this is potentially being called as the device is being shut down.
      updateProcesses(addedProcesses, removedProcessIds, level)
    }
  }

  private fun updateProcesses(addedClients: Collection<ClientSummary>, removedClients: Collection<Int>, level: ExposureLevel) {
    // Only update if device supported and online
    if (isDeviceApiSupported && ddmlibDevice.isOnline) {
      try {
        val timestampNs = serviceStub.getCurrentTime(TimeRequest.newBuilder().setStreamId(transportDevice.deviceId).build()).timestampNs
        addedClients.forEach { addProcess(it, timestampNs, level) }
        removedClients.forEach { removeProcess(it, timestampNs) }
      } catch (e: Exception) {
        // Most likely the destination server went down, and we're in shut down/disconnect mode.
        log.info(e)
      }
    }
  }

  private fun addProcess(client: ClientSummary, timestampNs: Long, level: ExposureLevel) = client.name.let { description ->
    // Process is started up and is ready
    // Parse cpu arch from client abi info, for example, "arm64" from "64-bit (arm64)". Abi string indicates whether application is
    // 64-bit or 32-bit and its cpu arch. Old devices of 32-bit do not have the application data, fall back to device's abi cpu arch.
    // TODO: Remove when moving process discovery.
    val processAbiCpuArch = client.abi.let { abi -> when {
      abi != null && ")" in abi -> abi.substring(abi.indexOf('(') + 1, abi.indexOf(')'))
      else -> Abi.getEnum(ddmlibDevice.abis[0])!!.cpuArch
    }}

    // TODO: Set this to the applications actual start time.
    val newProcess = Common.Process.newBuilder()
      .setName(description)
      .setPid(client.pid)
      .setDeviceId(transportDevice.deviceId)
      .setState(Common.Process.State.ALIVE)
      .setStartTimestampNs(timestampNs)
      .setAbiCpuArch(processAbiCpuArch)
      .setExposureLevel(level)
      .setPackageName(client.packageName)
      .build()
    cachedProcesses[client.pid] = newProcess
    // New pipeline event - create a ProcessStarted event for each process.
    proxyEventQueue.offer(
      Common.Event.newBuilder()
        .setGroupId(newProcess.pid.toLong())
        .setPid(newProcess.pid)
        .setKind(Common.Event.Kind.PROCESS)
        .setProcess(Common.ProcessData.newBuilder()
                      .setProcessStarted(Common.ProcessData.ProcessStarted.newBuilder().setProcess(newProcess)))
        .setTimestamp(timestampNs)
        .build()
    )
  }

  private fun removeProcess(clientPid: Int, timestampNs: Long) = cachedProcesses.remove(clientPid)?.let { process ->
    // New data pipeline event.
    proxyEventQueue.offer(
      Common.Event.newBuilder()
        .setGroupId(process.pid.toLong())
        .setPid(process.pid)
        .setKind(Common.Event.Kind.PROCESS)
        .setIsEnded(true)
        .setTimestamp(timestampNs)
        .build()
    )
  }

  companion object {
    private val log get() = Logger.getInstance(TransportServiceProxy::class.java)
    private const val EMULATOR = "Emulator"
    const val PRE_LOLLIPOP_FAILURE_REASON = "Pre-Lollipop devices are not supported."

    /**
     * Converts an [IDevice] object into a [Common.Device].
     *
     * @param device the IDevice to retrieve information from.
     * @return
     */
    @JvmStatic
    fun transportDeviceFromIDevice(device: IDevice): Common.Device {
      val bootId = device.getBootId()
      return Common.Device.newBuilder()
        .setDeviceId(device.getId(bootId))
        .setBootId(bootId)
        .setSerial(device.serialNumber)
        .setModel(getDeviceModel(device))
        .setVersion(StringUtil.notNullize(device.getProperty(IDevice.PROP_BUILD_VERSION)))
        .setCodename(StringUtil.notNullize(device.version.codename))
        .setApiLevel(device.version.apiLevel)
        .setFeatureLevel(device.version.featureLevel)
        .setManufacturer(getDeviceManufacturer(device))
        .setIsEmulator(device.isEmulator)
        .setBuildTags(device.getProperty(IDevice.PROP_BUILD_TAGS))
        .setBuildType(device.getProperty(IDevice.PROP_BUILD_TYPE))
        .setCpuAbi(device.getProperty(IDevice.PROP_DEVICE_CPU_ABI))
        .setState(convertState(device.state))
        .setUnsupportedReason(getDeviceUnsupportedReason(device))
        .build()
    }

    private fun IDevice.getId(bootId: String) = try {
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(bootId.toByteArray())
      digest.update(serialNumber.toByteArray())
      ByteBuffer.wrap(digest.digest()).long
    } catch (e: NoSuchAlgorithmException) {
      log.info("SHA-256 is not available", e)
      // Randomly generate an id if we cannot SHA.
      Random(System.currentTimeMillis()).nextLong()
    }

    private fun IDevice.getBootId(): String {
      var bootId: String? = null
      try {
        executeShellCommand("cat /proc/sys/kernel/random/boot_id", object : MultiLineReceiver() {
          override fun processNewLines(lines: Array<String>) {
            // There should only be one-line here.
            assert(lines.size == 1)
            // check for empty string because processNewLines() could be called twice.
            // For example if the output of the command terminates with new line,
            // it will be called once with the boot id, then with an empty string. We don't want to overwrite the first value.
            if (lines[0].isNotEmpty()) {
              bootId = lines[0]
            }
          }
          override fun isCancelled() = false
        })
      } catch (e: Exception) {
        when (e) {
          is TimeoutException, is AdbCommandRejectedException, is IOException, is ShellCommandUnresponsiveException ->
            log.warn("Unable to retrieve boot_id from device $this", e)
          else -> throw e
        }
      }
      return bootId ?: serialNumber.hashCode().toString()
    }

    private fun convertState(state: IDevice.DeviceState) = when (state) {
      IDevice.DeviceState.OFFLINE -> Common.Device.State.OFFLINE
      IDevice.DeviceState.ONLINE -> Common.Device.State.ONLINE
      IDevice.DeviceState.DISCONNECTED -> Common.Device.State.DISCONNECTED
      else -> Common.Device.State.UNSPECIFIED
    }

    private fun getDeviceUnsupportedReason(device: IDevice) = when {
      device.version.featureLevel < AndroidVersion.VersionCodes.LOLLIPOP -> PRE_LOLLIPOP_FAILURE_REASON
      else -> ""
    }

    @JvmStatic
    fun getDeviceModel(device: IDevice) = when {
      device.isEmulator -> StringUtil.notNullize(device.avdName, device.serialNumber)
      else -> DevicePropertyUtil.getModel(device, "Unknown")
    }

    @JvmStatic
    fun getDeviceManufacturer(device: IDevice) = DevicePropertyUtil.getManufacturer(device, if (device.isEmulator) EMULATOR else "")
  }
}

private typealias EventGrouping = MutableMap<Common.Event.Kind, Long2ObjectMap<Common.Event>>
private fun EventGrouping.remove(event: Common.Event) = computeIfPresent(event.kind) { _, map -> with (map) {
  remove(event.groupId)
  takeIf { it.isNotEmpty() }
}}
private fun EventGrouping.add(event: Common.Event) = compute(event.kind) { _, map -> (map ?: Long2ObjectOpenHashMap()).apply {
  put(event.groupId, event)
}}

private fun<V> StreamObserver<V>.onLast(response: V) {
  onNext(response)
  onCompleted()
}

private operator fun Int.contains(bitMask: Int) = this and bitMask != 0
private fun startThread(f: Runnable) = Thread(f).apply { start() }

/**
 * Adapter for `Client` and `ProfileableClient`
 *
 * Package name is empty  for profileable processes because it comes from JDWP's HELO.
 */
private data class ClientSummary(val pid: Int, val name: String, val packageName: String, val abi: String?) {
  companion object {
    fun of(client: Client) = with(client.clientData) { of(pid, clientDescription, packageName, abi) }
    fun of(client: ProfileableClient) =
      with(client.profileableClientData) { of(pid, processName.takeUnless { it.isEmpty() }, null, abi) }

    fun of(pid: Int, name: String?, packageName: String?, abi: String?) = name?.let { ClientSummary(pid, it, packageName ?: "", abi) }
  }
}
