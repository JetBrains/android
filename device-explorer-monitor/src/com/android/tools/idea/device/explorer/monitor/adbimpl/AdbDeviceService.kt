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
package com.android.tools.idea.device.explorer.monitor.adbimpl

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.monitor.DeviceService
import com.android.tools.idea.device.explorer.monitor.DeviceServiceListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.util.function.Supplier

class AdbDeviceService @NonInjectable constructor(private val adbSupplier: Supplier<File?>)
  : Disposable, DeviceService {

  @Suppress("unused")
  constructor(project: Project) : this({ AdbFileProvider.fromProject(project).get() })

  /**
   * The [CoroutineScope] used for cancelling coroutines when [dispose] is called.
   */
  private val coroutineScope: CoroutineScope = AndroidCoroutineScope(this)

  private val devices: MutableMap<String, IDevice> = HashMap()
  private val listeners: MutableList<DeviceServiceListener> = ArrayList()
  private var state = State.Initial
  private var bridge: AndroidDebugBridge? = null
  private val clientChangeListener = DeviceClientListener()
  private val deviceChangeListener = DeviceChangeListener()
  private val debugBridgeChangeListener = DebugBridgeChangeListener()
  private var deviceListSynced = CompletableDeferred<Unit>(coroutineScope.coroutineContext[Job])

  enum class State {
    Initial, SetupRunning, SetupDone
  }

  override fun dispose() {
    AndroidDebugBridge.removeClientChangeListener(clientChangeListener)
    AndroidDebugBridge.removeDeviceChangeListener(deviceChangeListener)
    AndroidDebugBridge.removeDebugBridgeChangeListener(debugBridgeChangeListener)
    bridge = null
    devices.clear()
    listeners.clear()
  }

  override fun addListener(listener: DeviceServiceListener) {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    listeners.add(listener)
  }

  override fun removeListener(listener: DeviceServiceListener) {
    ApplicationManagerEx.getApplication().assertIsDispatchThread()

    listeners.remove(listener)
  }

  override fun getIDeviceFromSerialNumber(serialNumber: String?): IDevice? {
    return if (serialNumber == null) {
      return null
    } else if (devices.containsKey(serialNumber)) {
      devices[serialNumber]
    } else {
      LOGGER.warn("Didn't find IDevice for serial number $serialNumber")
      null
    }
  }

  override suspend fun start() {
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
      AdbService.getInstance().getDebugBridge(adb).await()
      LOGGER.debug("Successfully obtained debug bridge")

      // Wait for the DebugBridgeChangeListener callback to execute before we return, so that we
      // have the initial device list. At this point, it will already be scheduled; we just need
      // to yield the UI thread to it.
      deviceListSynced.await()

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

  private inner class DebugBridgeChangeListener : AndroidDebugBridge.IDebugBridgeChangeListener {
    override fun bridgeChanged(bridge: AndroidDebugBridge?) {
      LOGGER.info("Debug bridge changed")
      coroutineScope.launch(AndroidDispatchers.uiThread) {
        if (this@AdbDeviceService.bridge != null) {
          devices.clear()
        }
        this@AdbDeviceService.bridge = bridge
        if (bridge != null) {
          if (bridge.hasInitialDeviceList()) {
            for (device in bridge.devices) {
              devices[device.serialNumber] = device
            }
          }
        }
        // From this point on, either we already got the device from the bridge,
        // or we'll hear about it in DeviceChangeListener.
        deviceListSynced.complete(Unit)
      }
    }
  }

  private inner class DeviceChangeListener : AndroidDebugBridge.IDeviceChangeListener {
    override fun deviceConnected(device: IDevice) {
      LOGGER.debug(String.format("Device connected: %s", device))
      devices[device.serialNumber] = device
    }

    override fun deviceDisconnected(device: IDevice) {
      LOGGER.debug(String.format("Device disconnected: %s", device))
      devices.remove(device.serialNumber)
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
      LOGGER.debug(String.format("Device changed: %s", device))
      coroutineScope.launch(AndroidDispatchers.uiThread) {
        devices[device.serialNumber]?.let {
          if (isMaskBitSet(changeMask, IDevice.CHANGE_CLIENT_LIST)) {
            listeners.forEach { l -> l.deviceProcessListUpdated(it) }
          }
        }
      }
    }
  }

  private inner class DeviceClientListener : AndroidDebugBridge.IClientChangeListener {
    override fun clientChanged(client: Client, changeMask: Int) {
      val device = client.device
      LOGGER.debug(String.format("Client changed: %s", device))
      coroutineScope.launch(AndroidDispatchers.uiThread) {
        devices[device.serialNumber]?.let {
          if (isMaskBitSet(changeMask, Client.CHANGE_INFO)) {
            listeners.forEach { l -> l.deviceProcessListUpdated(it) }
          }
        }
      }
    }
  }

  private fun isMaskBitSet(value: Int, mask: Int): Boolean {
    return (value and mask) != 0
  }

  companion object {
    fun getInstance(project: Project): AdbDeviceService = project.service()

    var LOGGER = logger<AdbDeviceService>()
  }
}