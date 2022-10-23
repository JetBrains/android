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
package com.android.tools.idea.editors.liveedit.ui

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.emulator.SERIAL_NUMBER_KEY
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

/**
 * Maps device serial numbers to IDevice.
 */
class EmulatorLiveEditAdapter(val project: Project): LiveEditAction.DeviceGetter {
  private var cachedDeviceFutures = ConcurrentHashMap<String, ListenableFuture<IDevice?>>()

  fun register(serial: String) {
    cachedDeviceFutures[serial] = Futures.immediateCancelledFuture()
  }

  fun unregister(serial: String) {
    cachedDeviceFutures.remove(serial)?.cancel(true)
  }

  override fun serial(dataContext: DataContext): String? {
    return dataContext.getData(SERIAL_NUMBER_KEY)
  }

  override fun device(dataContext: DataContext): IDevice? {
    val serial = serial(dataContext) ?: return null
    val future = cachedDeviceFutures[serial] ?: return null
    if (!future.isCancelled) {
      if (future.isDone) {
        val device = future.get()
        if (device != null) {
          return device
        }
        // else we try to get the device again
      } else {
        return null
      }
    }

    val adbFile = AndroidSdkUtils.findAdb(project).adbPath ?: throw Exception("Could not find adb executable")
    val bridgeFuture: ListenableFuture<AndroidDebugBridge> = AdbService.getInstance().getDebugBridge(adbFile)
    cachedDeviceFutures[serial] = bridgeFuture.transform(AppExecutorUtil.getAppExecutorService()) { debugBridge ->
      debugBridge.devices.find { it.serialNumber == serial }
    }
    return null
  }

  override fun devices(): List<IDevice> {
    return cachedDeviceFutures.values.stream()
      .filter { it.isDone && !it.isCancelled }
      .map { it.get() }
      .filter { it != null } // filters out nulls
      .map { it!! } // cast it so that it's not IDevice? (nullable)
      .toList()
  }
}