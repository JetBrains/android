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
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
class AdbDeviceFileSystemService private constructor() : Disposable, DeviceFileSystemService<AdbDeviceFileSystem> {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AdbDeviceFileSystemService = project.service()

    var LOGGER = logger<AdbDeviceFileSystemService>()
  }

  private val coroutineScope = AndroidCoroutineScope(this)
  private val edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
  private val taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)
  private val myDevices: MutableList<AdbDeviceFileSystem> = ArrayList()
  private val myListeners: MutableList<DeviceFileSystemServiceListener> = ArrayList()
  private var myState = State.Initial
  private var myBridge: AndroidDebugBridge? = null
  private var myDeviceChangeListener: DeviceChangeListener? = null
  private var myDebugBridgeChangeListener: DebugBridgeChangeListener? = null
  private var myAdb: File? = null
  private var myStartServiceFuture = SettableFuture.create<Unit>()

  override fun dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener)
    AndroidDebugBridge.removeDebugBridgeChangeListener(myDebugBridgeChangeListener)
    myBridge = null
    myDevices.clear()
    myListeners.clear()
  }

  enum class State {
    Initial, SetupRunning, SetupDone
  }

  val deviceList: List<AdbDeviceFileSystem>
    get() = myDevices

  override fun addListener(listener: DeviceFileSystemServiceListener) {
    myListeners.add(listener)
  }

  override fun removeListener(listener: DeviceFileSystemServiceListener) {
    myListeners.remove(listener)
  }

  /**
   * Starts the service using an ADB File.
   *
   *
   * If this method is called when the service is starting or is already started, it returns immediately.
   *
   * To restart the service using a different ADB file, call [AdbDeviceFileSystemService.restart]
   */
  @UiThread
  override suspend fun start(adbSupplier: Supplier<File?>) {
    if (myState == State.SetupRunning || myState == State.SetupDone) {
      return
    }
    val adb = adbSupplier.get()
    if (adb == null) {
      LOGGER.warn("ADB not found")
      throw FileNotFoundException("Android Debug Bridge not found.")
    }
    myAdb = adb
    myDeviceChangeListener = DeviceChangeListener()
    myDebugBridgeChangeListener = DebugBridgeChangeListener()
    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener!!)
    AndroidDebugBridge.addDebugBridgeChangeListener(myDebugBridgeChangeListener!!)
    return startDebugBridge()
  }

  @UiThread
  private suspend fun startDebugBridge() {
    val adb = checkNotNull(myAdb)
    myState = State.SetupRunning
    myStartServiceFuture = SettableFuture.create()
    try {
      // We don't actually assign to myBridge here, we do that in the DebugBridgeChangeListener
      AdbService.getInstance().getDebugBridge(adb).await()
      LOGGER.info("Successfully obtained debug bridge")
      myState = State.SetupDone
    } catch (t: Throwable) {
      LOGGER.warn("Unable to obtain debug bridge", t)
      myState = State.Initial
      if (t.message != null) {
        throw t
      } else {
        throw RuntimeException(AdbService.getDebugBridgeDiagnosticErrorMessage(t, adb), t)
      }
    }
  }

  @UiThread
  override suspend fun restart(adbSupplier: Supplier<File?>) {
    if (myState == State.Initial) {
      return start(adbSupplier)
    }
    checkState(State.SetupDone)

    withContext(workerThread) {
      AdbService.getInstance().terminateDdmlib()
    }

    startDebugBridge()
  }

  override val devices: List<AdbDeviceFileSystem>
    get() {
      checkState(State.SetupDone)
      return myDevices
    }

  private fun checkState(state: State) {
    check(myState == state)
  }

  private inner class DebugBridgeChangeListener : IDebugBridgeChangeListener {
    override fun bridgeChanged(bridge: AndroidDebugBridge?) {
      LOGGER.info("Debug bridge changed")
      coroutineScope.launch(uiThread) {
        if (myBridge != null) {
          myDevices.clear()
          myListeners.forEach { it.serviceRestarted() }
          myBridge = null
        }
        if (bridge != null) {
          myBridge = bridge
          if (bridge.hasInitialDeviceList()) {
            for (device in bridge.devices) {
              myDevices.add(AdbDeviceFileSystem(device, edtExecutor, taskExecutor))
            }
          }
        }
      }
    }
  }

  private inner class DeviceChangeListener : IDeviceChangeListener {
    override fun deviceConnected(device: IDevice) {
      LOGGER.info(String.format("Device connected: %s", device))
      coroutineScope.launch(uiThread) {
        if (findDevice(device) == null) {
          val newDevice = AdbDeviceFileSystem(device, edtExecutor, taskExecutor)
          myDevices.add(newDevice)
          myListeners.forEach { it.deviceAdded(newDevice) }
        }
      }
    }

    override fun deviceDisconnected(device: IDevice) {
      LOGGER.info(String.format("Device disconnected: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          myListeners.forEach { l -> l.deviceRemoved(it) }
          myDevices.remove(it)
        }
      }
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
      LOGGER.info(String.format("Device changed: %s", device))
      coroutineScope.launch(uiThread) {
        findDevice(device)?.let {
          myListeners.forEach { l -> l.deviceUpdated(it) }
        }
      }
    }

    private fun findDevice(device: IDevice): AdbDeviceFileSystem? {
      return myDevices.find { it.isDevice(device) }
    }
  }
}