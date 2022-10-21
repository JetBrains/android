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
package com.android.tools.idea.device.monitor.adbimpl

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.Client.CHANGE_INFO
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceListService
import com.android.tools.idea.device.monitor.processes.DeviceListServiceListener
import com.android.tools.idea.device.monitor.processes.ProcessInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.function.Supplier

/**
 * Implementation of [DeviceListService] specific to [AndroidDebugBridge] and [Client] interaction.
 */
@UiThread
class AdbDeviceListService @NonInjectable constructor(private val adbSupplier: Supplier<File?>)
  : Disposable, DeviceListService {

  @Suppress("unused")
  constructor(project: Project) : this({ AdbFileProvider.fromProject(project).get() })

  companion object {
    fun getInstance(project: Project): AdbDeviceListService = project.service()

    var LOGGER = logger<AdbDeviceListService>()
  }

  /**
   * The [CoroutineScope] used for cancelling coroutines when [dispose] is called.
   */
  private val coroutineScope: CoroutineScope = AndroidCoroutineScope(this)

  /**
   * The [CoroutineDispatcher] used for asynchronous work that **cannot** happen on the EDT thread.
   */
  private val workerThreadDispatcher: CoroutineDispatcher = workerThread

  /**
   * Each device connected to ADB is represented by an AdbDeviceFileSystem here.
   * The list is initially populated in the DebugBridgeChangeListener, then maintained
   * by the DeviceChangeListener.
   */
  private val myDevices: MutableMap<String, AdbDevice> = HashMap()
  private val listeners: MutableList<DeviceListServiceListener> = ArrayList()
  private var state = State.Initial
  private var bridge: AndroidDebugBridge? = null
  private val clientChangeListener = DeviceClientListener()
  private val deviceChangeListener = DeviceChangeListener()
  private val debugBridgeChangeListener = DebugBridgeChangeListener()

  override fun dispose() {
    AndroidDebugBridge.removeClientChangeListener(clientChangeListener)
    AndroidDebugBridge.removeDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.removeDebugBridgeChangeListener(debugBridgeChangeListener)
    bridge = null
    myDevices.clear()
    listeners.clear()
  }

  enum class State {
    Initial, SetupRunning, SetupDone
  }

  override fun addListener(listener: DeviceListServiceListener) {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    listeners.add(listener)
  }

  override fun removeListener(listener: DeviceListServiceListener) {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    listeners.remove(listener)
  }

  /**
   * Starts the service using an ADB File.
   *
   * If this method is called when the service is starting or is already started, it returns immediately.
   */
  @UiThread
  override suspend fun start() {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    // Ensure `startWorker` is cancelled if we are disposed during execution
    @Suppress("RedundantAsync")
    coroutineScope.async(uiThread) {
      startWorker()
    }.await()
  }

  override val devices: List<AdbDevice>
    get() {
      ApplicationManagerEx.getApplication().assertIsDispatchThread()

      return when (state) {
        State.Initial, State.SetupRunning -> emptyList()
        State.SetupDone -> myDevices.values.toList()
      }
    }

  /**
   * Returns the list of [ProcessInfo] of the device.
   */
  override suspend fun fetchProcessList(device: Device): List<ProcessInfo> {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()
    checkState(State.SetupDone)

    // Run this in a worker thread in case the device/adb is not responsive
    val adbDevice = myDevices[device.serialNumber] ?: return emptyList()
    val clients = adbDevice.device.clients ?: emptyArray()
    return withContext(workerThreadDispatcher) {
      clients
        .asSequence()
        .mapNotNull { client -> createProcessInfo(adbDevice, client) }
        .toList()
    }
  }

  /**
   * Kills the [process] on the [device][ProcessInfo.device]
   */
  override suspend fun killProcess(process: ProcessInfo) {
    invokeOnDevice(process.device.serialNumber) {
      it.kill(process.processName)
    }
  }

  /**
   * Kills the [process] on the [device][ProcessInfo.device]
   */
  override suspend fun forceStopProcess(process: ProcessInfo) {
    invokeOnDevice(process.device.serialNumber) {
      it.forceStop(process.processName)
    }
  }

  private suspend fun invokeOnDevice(serialNumber: String, block: (IDevice) -> Unit) {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()
    checkState(State.SetupDone)

    myDevices[serialNumber]?.device?.let { device ->
      // Run this in a worker thread in case the device/adb is not responsive
      withContext(workerThreadDispatcher) {
        block(device)
      }
    }
  }

  private suspend fun startWorker() {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    if (state == State.SetupRunning || state == State.SetupDone) {
      return
    }
    val adb = adbSupplier.get()
    if (adb == null) {
      LOGGER.warn("ADB not found")
      throw FileNotFoundException("Android Debug Bridge not found.")
    }

    AndroidDebugBridge.addClientChangeListener(clientChangeListener)
    AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.addDebugBridgeChangeListener(debugBridgeChangeListener)

    state = State.SetupRunning
    try {
      // We don't actually assign to myBridge here, we do that in the DebugBridgeChangeListener
      val bridge = AdbService.getInstance().getDebugBridge(adb).await()
      LOGGER.debug("Successfully obtained debug bridge")

      bridgeChangedWorker(bridge)
      state = State.SetupDone
    }
    catch (t: Throwable) {
      LOGGER.warn("Unable to obtain debug bridge", t)
      state = State.Initial
      if (t.message != null) {
        throw t
      }
      else {
        throw RuntimeException(AdbService.getDebugBridgeDiagnosticErrorMessage(t, adb), t)
      }
    }
  }

  private fun bridgeChangedWorker(newBridge: AndroidDebugBridge?) {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    if (this.bridge != null) {
      myDevices.clear()
      listeners.forEach { it.serviceRestarted() }
    }
    this.bridge = newBridge
    if (newBridge != null) {
      if (newBridge.hasInitialDeviceList()) {
        for (device in newBridge.devices) {
          myDevices[device.serialNumber] = AdbDevice(device)
        }
      }
    }
  }

  @WorkerThread
  private fun createProcessInfo(device: AdbDevice, client: Client): ProcessInfo? {
    try {
      val processName = client.clientData.clientDescription
      return if (processName == null) {
        thisLogger().debug(
          "Process ${client.clientData.pid} was skipped because the process name is not initialized. Is another instance of Studio running?")
        ProcessInfo(device,
                    pid = client.clientData.pid)
      }
      else {
        val userId = if (client.clientData.userId == -1) null else client.clientData.userId
        ProcessInfo(device,
                    pid = client.clientData.pid,
                    processName = processName,
                    userId = userId,
                    vmIdentifier = client.clientData.vmIdentifier,
                    abi = client.clientData.abi,
                    debuggerStatus = client.clientData.debuggerConnectionStatus,
                    supportsNativeDebugging = client.clientData.isNativeDebuggable)
      }
    }
    catch (e: Throwable) {
      thisLogger().warn("Error retrieving process info from `Client`", e)
      return null
    }
  }

  private fun checkState(@Suppress("SameParameterValue") state: State) {
    check(this.state == state)
  }

  private inner class DebugBridgeChangeListener : IDebugBridgeChangeListener {
    override fun bridgeChanged(bridge: AndroidDebugBridge?) {
      LOGGER.debug("Debug bridge changed")
      coroutineScope.launch(uiThread) {
        bridgeChangedWorker(bridge)
      }
    }
  }

  private inner class DeviceChangeListener : IDeviceChangeListener {
    override fun deviceConnected(device: IDevice) {
      LOGGER.debug(String.format("Device connected: %s", device))
      coroutineScope.launch(uiThread) {
        if (findDevice(device) == null) {
          val newDevice = AdbDevice(device)
          myDevices[newDevice.serialNumber] = newDevice
          listeners.forEach { it.deviceAdded(newDevice) }
        }
      }
    }

    override fun deviceDisconnected(device: IDevice) {
      LOGGER.debug(String.format("Device disconnected: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          listeners.forEach { l -> l.deviceRemoved(it) }
          myDevices.remove(it.serialNumber)
        }
      }
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
      LOGGER.debug(String.format("Device changed: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          if (isMaskBitSet(changeMask, CHANGE_STATE)) {
            listeners.forEach { l -> l.deviceUpdated(it) }
          }
          if (isMaskBitSet(changeMask, CHANGE_CLIENT_LIST)) {
            listeners.forEach { l -> l.deviceProcessListUpdated(it) }
          }
        }
      }
    }
  }

  private inner class DeviceClientListener : IClientChangeListener {
    override fun clientChanged(client: Client, changeMask: Int) {
      val device = client.device
      LOGGER.debug(String.format("Client changed: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          if (isMaskBitSet(changeMask, CHANGE_INFO)) {
            listeners.forEach { l -> l.deviceProcessListUpdated(it) }
          }
        }
      }
    }
  }

  private fun isMaskBitSet(value: Int, mask: Int): Boolean {
    return (value and mask) != 0
  }

  private fun findDevice(device: IDevice): AdbDevice? {
    return myDevices[device.serialNumber]
  }
}
