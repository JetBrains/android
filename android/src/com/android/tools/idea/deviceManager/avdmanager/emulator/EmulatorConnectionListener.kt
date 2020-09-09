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
package com.android.tools.idea.deviceManager.avdmanager.emulator

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.run.util.LaunchUtils
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.Uninterruptibles
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A utility class to wait for an emulator to be fully launched (ready for "pm install") and connected to adb.  */
object EmulatorConnectionListener {
  // Wait for a device corresponding to given emulator to come online for the given timeout period
  @JvmStatic
  fun getDeviceForEmulator(
    project: Project?,
    avdName: String,
    emulatorProcessHandler: ProcessHandler?,
    timeoutSeconds: Long,
    units: TimeUnit
  ): ListenableFuture<IDevice> {
    val pollTimeout = TimeUnit.SECONDS

    fun isEmulatorReady(device: IDevice): Boolean {
      if (!device.isOnline) {
        return false
      }
      val bootComplete = device.getProperty("dev.bootcomplete")
      if (bootComplete == null) {
        logger<EmulatorConnectionListener>().warn("Emulator not ready yet, dev.bootcomplete = null")
        return false
      }
      return true
    }

    if (emulatorProcessHandler == null) {
      return Futures.immediateFailedFuture(RuntimeException("Emulator process for AVD $avdName died."))
    }

    val deviceFuture = SettableFuture.create<IDevice>()
    executeOnPooledThread {
      val timeout: Long = pollTimeout.convert(timeoutSeconds, units)
      val adb = AndroidSdkUtils.getAdb(project)
      if (adb == null) {
        deviceFuture.setException(IllegalArgumentException("Unable to locate adb"))
        return@executeOnPooledThread
      }
      for (i in 0 until timeout) {
        if (deviceFuture.isCancelled) {
          return@executeOnPooledThread
        }
        if (emulatorProcessHandler.isProcessTerminated || emulatorProcessHandler.isProcessTerminating) {
          deviceFuture.setException(RuntimeException("The emulator process for AVD $avdName was killed."))
          return@executeOnPooledThread
        }
        val bridgeFuture = AdbService.getInstance().getDebugBridge(adb)
        val bridge: AndroidDebugBridge? = try {
          bridgeFuture[1, pollTimeout]
        }
        catch (e: TimeoutException) {
          continue
        }
        catch (e: Exception) {
          deviceFuture.setException(e)
          return@executeOnPooledThread
        }
        if (bridge == null || !bridge.isConnected) {
          deviceFuture.setException(RuntimeException("adb connection not available, or was terminated."))
          return@executeOnPooledThread
        }

        bridge.devices
          .filter { it.isEmulator }
          .filter { StringUtil.equals(it.avdName, avdName) }
          .forEach { device ->
            // now it looks like the AVD is online, but we still have to wait for the AVD to be ready for installation
            if (isEmulatorReady(device)) {
              LaunchUtils.initiateDismissKeyguard(device)
              deviceFuture.set(device)
              return@executeOnPooledThread
            }
          }

        // sleep for a while
        Uninterruptibles.sleepUninterruptibly(1, pollTimeout)
      }
      val msg = "Timed out after ${pollTimeout.toSeconds(timeout)}seconds waiting for emulator to come online."
      deviceFuture.setException(TimeoutException(msg))
      logger<EmulatorConnectionListener>().warn(msg)
    }
    return deviceFuture
  }
}