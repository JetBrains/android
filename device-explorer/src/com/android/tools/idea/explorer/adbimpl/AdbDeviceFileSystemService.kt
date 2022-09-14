/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.jetbrains.ide.PooledThreadExecutor
import java.io.File
import java.io.FileNotFoundException
import java.util.function.Supplier

/**
 * Abstraction over ADB devices and their file system.
 * The service is meant to be called on the EDT thread, where
 * long running operations either raise events or return a Future.
 */
@UiThread
class AdbDeviceFileSystemService @NonInjectable constructor (private val adbSupplier: Supplier<File?>)
    : Disposable, DeviceFileSystemService<AdbDeviceFileSystem> {

  constructor(project: Project) : this({ AdbFileProvider.fromProject(project)?.adbFile })

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AdbDeviceFileSystemService = project.service()

    var LOGGER = logger<AdbDeviceFileSystemService>()
  }

  private val coroutineScope = AndroidCoroutineScope(this)
  private val edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
  private val dispatcher = PooledThreadExecutor.INSTANCE.asCoroutineDispatcher()
  /**
   * Each device connected to ADB is represented by an AdbDeviceFileSystem here.
   * The list is initially populated in the DebugBridgeChangeListener, then maintained
   * by the DeviceChangeListener.
   */
  private val myDevices: MutableList<AdbDeviceFileSystem> = ArrayList()
  private val listeners: MutableList<DeviceFileSystemServiceListener> = ArrayList()
  private var state = State.Initial
  private var bridge: AndroidDebugBridge? = null
  private val deviceChangeListener = DeviceChangeListener()
  private val debugBridgeChangeListener = DebugBridgeChangeListener()
  private var adb: File? = null
  private var deviceListSynced = CompletableDeferred<Unit>(coroutineScope.coroutineContext[Job])

  override fun dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.removeDebugBridgeChangeListener(debugBridgeChangeListener)
    bridge = null
    myDevices.clear()
    listeners.clear()
  }

  enum class State {
    Initial, SetupRunning, SetupDone
  }

  override fun addListener(listener: DeviceFileSystemServiceListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: DeviceFileSystemServiceListener) {
    listeners.remove(listener)
  }

  /**
   * Starts the service using an ADB File.
   *
   * If this method is called when the service is starting or is already started, it returns immediately.
   */
  @UiThread
  override suspend fun start() {
    if (state == State.SetupRunning || state == State.SetupDone) {
      return
    }
    val adb = adbSupplier.get()
    if (adb == null) {
      LOGGER.warn("ADB not found")
      throw FileNotFoundException("Android Debug Bridge not found.")
    }
    this.adb = adb

    AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.addDebugBridgeChangeListener(debugBridgeChangeListener)

    state = State.SetupRunning
    try {
      // We don't actually assign to myBridge here, we do that in the DebugBridgeChangeListener
      AdbService.getInstance().getDebugBridge(adb).await()
      LOGGER.info("Successfully obtained debug bridge")

      // Wait for the DebugBridgeChangeListener callback to execute before we return, so that we
      // have the initial device list. At this point, it will already be scheduled; we just need
      // to yield the UI thread to it.
      deviceListSynced.await()

      state = State.SetupDone
    } catch (t: Throwable) {
      LOGGER.warn("Unable to obtain debug bridge", t)
      state = State.Initial
      if (t.message != null) {
        throw t
      } else {
        throw RuntimeException(AdbService.getDebugBridgeDiagnosticErrorMessage(t, adb), t)
      }
    }
  }

  override val devices: List<AdbDeviceFileSystem>
    get() {
      checkState(State.SetupDone)
      return myDevices
    }

  private fun checkState(state: State) {
    check(this.state == state)
  }

  private inner class DebugBridgeChangeListener : IDebugBridgeChangeListener {
    override fun bridgeChanged(bridge: AndroidDebugBridge?) {
      LOGGER.info("Debug bridge changed")
      coroutineScope.launch(uiThread) {
        if (this@AdbDeviceFileSystemService.bridge != null) {
          myDevices.clear()
          listeners.forEach { it.serviceRestarted() }
        }
        this@AdbDeviceFileSystemService.bridge = bridge
        if (bridge != null) {
          if (bridge.hasInitialDeviceList()) {
            for (device in bridge.devices) {
              myDevices.add(AdbDeviceFileSystem(coroutineScope, device, edtExecutor, dispatcher))
            }
          }
        }
        // From this point on, either we already got the device from the bridge,
        // or we'll hear about it in DeviceChangeListener.
        deviceListSynced.complete(Unit)
      }
    }
  }

  private inner class DeviceChangeListener : IDeviceChangeListener {
    override fun deviceConnected(device: IDevice) {
      LOGGER.info(String.format("Device connected: %s", device))
      coroutineScope.launch(uiThread) {
        if (findDevice(device) == null) {
          val newDevice = AdbDeviceFileSystem(coroutineScope, device, edtExecutor, dispatcher)
          myDevices.add(newDevice)
          listeners.forEach { it.deviceAdded(newDevice) }
        }
      }
    }

    override fun deviceDisconnected(device: IDevice) {
      LOGGER.info(String.format("Device disconnected: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          listeners.forEach { l -> l.deviceRemoved(it) }
          myDevices.remove(it)
        }
      }
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
      LOGGER.info(String.format("Device changed: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          listeners.forEach { l -> l.deviceUpdated(it) }
        }
      }
    }

    private fun findDevice(device: IDevice): AdbDeviceFileSystem? {
      return myDevices.find { it.isDevice(device) }
    }
  }
}