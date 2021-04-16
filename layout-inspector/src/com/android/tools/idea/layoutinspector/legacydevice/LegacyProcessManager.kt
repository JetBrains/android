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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.ddms.DevicePropertyUtil
import com.android.tools.idea.layoutinspector.transport.InspectorProcessManager
import com.android.tools.idea.util.ListenerCollection
import com.android.tools.profiler.proto.Common
import com.android.utils.HashCodes
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

private const val MAX_RETRY_COUNT = 100

/**
 * A process manager that keeps track of the available processes for the Layout Inspector.
 *
 * This class uses the AndroidDebugBridge to listen for changes in the list of active devices,
 * and their associated processes.
 */
class LegacyProcessManager(
  parentDisposable: Disposable,
  @TestOnly val scheduler: ScheduledExecutorService = JobScheduler.getScheduler()
) : InspectorProcessManager, Disposable {
  override val processListeners = ListenerCollection.createWithDirectExecutor<() -> Unit>()

  /**
   * Map from a device serial number to a [DeviceSpec]
   */
  private val devices = ConcurrentHashMap<String, DeviceSpec>()

  /**
   * Map from a device serial number to a process id to a [ProcessSpec]
   */
  private val processes = ConcurrentHashMap<String, ConcurrentHashMap<Int, ProcessSpec>>()

  private val listenerLock = Any()

  private val clientChangeListener = AndroidDebugBridge.IClientChangeListener { client, _ ->
    synchronized(listenerLock) {
      replaceClientAndReport(client)
    }
  }

  private val deviceChangeListener = object : AndroidDebugBridge.IDeviceChangeListener {
    override fun deviceConnected(device: IDevice) {
      synchronized(listenerLock) {
        addDevice(device)
        replaceProcesses(device)
      }
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
      synchronized(listenerLock) {
        if (!devices.contains(device.serialNumber)) {
          addDevice(device)
        }
        if (changeMask and IDevice.CHANGE_CLIENT_LIST > 0) {
          replaceProcesses(device)
        }
      }
    }

    override fun deviceDisconnected(device: IDevice) {
      synchronized(listenerLock) {
        removeDevice(device)
      }
    }
  }

  init {
    Disposer.register(parentDisposable, this)
    AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.addClientChangeListener(clientChangeListener)
  }

  override fun dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.removeClientChangeListener(clientChangeListener)
  }

  override fun getStreams(): Sequence<Common.Stream> = devices.values.asSequence().map { it.stream }

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> {
    val streamProcesses = processes[stream.device.serial] ?: return emptySequence()
    return streamProcesses.values.asSequence().map { it.process }
  }

  override fun isProcessActive(stream: Common.Stream, process: Common.Process): Boolean =
    processes[stream.device.serial]?.get(process.pid)?.process == process

  /**
   * Returns the client for a specified process
   */
  fun findClientFor(stream: Common.Stream, process: Common.Process): Client? = processes[stream.device.serial]?.get(process.pid)?.client

  /**
   * Returns the client for a specified process
   */
  fun findIDeviceFor(stream: Common.Stream): IDevice? = devices[stream.device.serial]?.device

  private fun addDevice(iDevice: IDevice) {
    // When we ask for the first property, the properties will be loaded asynchronously.
    iDevice.getSystemProperty(IDevice.PROP_DEVICE_MODEL)

    // Wait for the properties to be loaded:
    addDeviceWhenPropertiesAreLoaded(iDevice, 0)
  }

  private fun addDeviceWhenPropertiesAreLoaded(iDevice: IDevice, timesAttempted: Int) {
    when {
      timesAttempted > MAX_RETRY_COUNT -> return
      iDevice.state == IDevice.DeviceState.DISCONNECTED -> return
      iDevice.arePropertiesSet() -> devices.getOrPut(iDevice.serialNumber) { DeviceSpec(iDevice) }
      else -> scheduler.schedule({ addDeviceWhenPropertiesAreLoaded(iDevice, timesAttempted + 1) }, 50, TimeUnit.MILLISECONDS)
    }
  }

  private fun removeDevice(iDevice: IDevice) {
    val serialNumber = iDevice.serialNumber
    devices.remove(serialNumber)
    removeOldProcesses(serialNumber, emptyArray())
    processes.remove(serialNumber)
    fireProcessesChanged()
  }

  private fun replaceClientAndReport(client: Client) {
    if (replaceClient(client)) {
      fireProcessesChanged()
    }
  }

  private fun replaceProcesses(iDevice: IDevice) {
    var changes = false
    iDevice.clients.forEach { changes = replaceClient(it) || changes }
    changes = removeOldProcesses(iDevice) || changes
    if (changes) {
      fireProcessesChanged()
    }
  }

  private fun removeOldProcesses(device: IDevice): Boolean {
    return removeOldProcesses(device.serialNumber, device.clients)
  }

  /**
   * Remove all processes from [processes] that isn't one of the specified [currentClients]
   *
   * @return true if a change in the cached information was made, otherwise false
   */
  private fun removeOldProcesses(serialNumber: String, currentClients: Array<Client>): Boolean {
    val streamProcesses = processes[serialNumber] ?: return false
    val oldIds = streamProcesses.keys.toMutableSet()
    currentClients.forEach { oldIds.remove(it.clientData.pid) }
    if (oldIds.isEmpty()) {
      return false
    }
    oldIds.forEach {
      streamProcesses.remove(it)
    }
    return true
  }

  /**
   * Replace the cached information about the specified [client]
   *
   * This function performs the following:
   *  - if the [client] is unwanted remove the client from the cache
   *  - otherwise add (or update) the cache with the client information
   *
   * @return true if a change in the cached information was made, otherwise false
   */
  private fun replaceClient(client: Client): Boolean {
    val processSpec = toWantedProcess(client) ?: return removeClient(client)
    return addClient(processSpec)
  }

  /**
   * Add a process to the cached information
   *
   * @return true if a change in the cached information was made, otherwise false
   */
  private fun addClient(processSpec: ProcessSpec): Boolean {
    val device = processSpec.client.device
    val streamProcesses = processes.getOrPut(device.serialNumber, { ConcurrentHashMap() })
    val oldProcessSpec = streamProcesses[processSpec.process.pid]
    if (oldProcessSpec == processSpec) {
      return false
    }
    streamProcesses[processSpec.process.pid] = processSpec
    return true
  }

  /**
   * Remove a client from the cached information
   *
   * @return true if a change in the cached information was made, otherwise false
   */
  private fun removeClient(client: Client): Boolean {
    val device = client.device
    val streamProcesses = processes[device.serialNumber] ?: return false
    return streamProcesses.remove(client.clientData.pid) != null
  }

  /**
   * Convert a [client] to a [ProcessSpec]
   *
   * This function performs the following:
   *  - for a new relevant [client] a ProcessSpec is simply created and returned
   *  - for a known [client] return the ProcessSpec already associated with the [client]
   *  - a known [client] where the properties have changed, create a new [Common.Process]
   *  - exception: return <code>null</code> if we don't want to keep track of this [client]
   */
  private fun toWantedProcess(client: Client): ProcessSpec? {
    if (!client.clientData.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY) ||
         client.clientData.packageName.isNullOrEmpty() ||
         client.clientData.packageName == ClientData.PRE_INITIALIZED) {
      return null
    }
    val processSpec = processes[client.device.serialNumber]?.get(client.clientData.pid) ?: return ProcessSpec(client)
    return if (processSpec.isUpToDate(client)) processSpec else ProcessSpec(client)
  }

  private fun fireProcessesChanged() {
    processListeners.forEach(Consumer { it() })
  }

  /**
   * Holds a [IDevice] with an associated [Common.Stream]
   */
  private class DeviceSpec(val device: IDevice) {
    val stream: Common.Stream = createStream(device)

    private fun createStream(iDevice: IDevice): Common.Stream {
      val deviceProto = Common.Device.newBuilder().apply {
        apiLevel = iDevice.version.apiLevel
        codename = iDevice.version.codename ?: ""
        featureLevel = iDevice.version.featureLevel
        isEmulator = iDevice.isEmulator
        manufacturer = DevicePropertyUtil.getManufacturer(iDevice, "")
        model = iDevice.avdName ?: DevicePropertyUtil.getModel(iDevice, "")
        serial = iDevice.serialNumber
        version = SdkVersionInfo.getVersionString(iDevice.version.apiLevel)
      }.build()
      return Common.Stream.newBuilder().apply {
        device = deviceProto
        streamId = iDevice.hashCode().toLong()
      }.build()
    }
  }

  /**
   * Holds a [Client] with an associated [Common.Process]
   */
  private class ProcessSpec(val client: Client) {
    val process: Common.Process = createProcess(client)

    private fun createProcess(client: Client): Common.Process =
      Common.Process.newBuilder().apply {
        abiCpuArch = client.clientData.abi ?: ""
        name = client.clientData.packageName
        pid = client.clientData.pid
      }.build()

    /**
     * Returns true if the [process] and [client] is up to date with [newClient]
     */
    fun isUpToDate(newClient: Client): Boolean =
      client == newClient &&
      process.abiCpuArch == (client.clientData.abi ?: "") &&
      process.name == client.clientData.packageName &&
      process.pid == client.clientData.pid

    override fun hashCode(): Int {
      return HashCodes.mix(client.hashCode(), process.hashCode())
    }

    override fun equals(other: Any?): Boolean {
      val spec = other as? ProcessSpec ?: return false
      return client == spec.client && process == spec.process
    }
  }
}
