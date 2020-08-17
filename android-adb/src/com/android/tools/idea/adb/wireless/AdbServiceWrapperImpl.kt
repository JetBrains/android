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
package com.android.tools.idea.adb.wireless

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.TimeoutRemainder
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.executeAsync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class AdbServiceWrapperImpl(
  private val project: Project,
  private val nanoTimeProvider: TimeoutRemainder.SystemNanoTimeProvider,
  val taskExecutor: ListeningExecutorService
) : AdbServiceWrapper {
  private val ADB_TIMEOUT_MILLIS = 30_000L
  private val ADB_DEVICE_CONNECT_MILLIS = 30_000L
  private val LOG = logger<AdbServiceWrapperImpl>()

  override fun executeCommand(args: List<String>, stdin: String): ListenableFuture<AdbCommandResult> {
    return getAdbLocation().transform(taskExecutor) { adbFile ->
      // Execute ADB command, capturing output and exit value
      val stdinStream = stdin.byteInputStream()
      val stdoutStream = ByteArrayOutputStream()
      val stderrStream = ByteArrayOutputStream()
      val exitValue = ExternalCommand(adbFile.absolutePath).execute(args, stdinStream, stdoutStream, stderrStream, ADB_TIMEOUT_MILLIS,
                                                                    TimeUnit.MILLISECONDS)
      val processOutput = ProcessOutput()
      processOutput.appendStdout(stdoutStream.toString("UTF-8"))
      processOutput.appendStderr(stderrStream.toString("UTF-8"))
      AdbCommandResult(exitValue, processOutput.stdoutLines, processOutput.stderrLines)
    }
  }

  override fun waitForOnlineDevice(pairingResult: PairingResult): ListenableFuture<AdbOnlineDevice> {
    return getAdbLocation().transformAsync(taskExecutor) { adbFile ->
      AdbService.getInstance().getDebugBridge(adbFile)
    }.transform(taskExecutor) { debugBridge ->
      waitForDevice(debugBridge, pairingResult)
    }
  }

  private fun getAdbLocation(): ListenableFuture<File> {
    return taskExecutor.executeAsync {
      val adbProvider = AdbFileProvider.fromProject(project)
      if (adbProvider == null) {
        LOG.warn("AdbFileProvider is not correctly set up (see AdbFileProviderInitializer)")
      }
      adbProvider?.adbFile ?: throw IllegalStateException("The path to the ADB command is not available")
    }
  }

  private fun waitForDevice(debugBridge: AndroidDebugBridge, pairingResult: PairingResult): AdbOnlineDevice {
    val rem = TimeoutRemainder(nanoTimeProvider, ADB_DEVICE_CONNECT_MILLIS, TimeUnit.MILLISECONDS)
    while (true) {
      val device = debugBridge.devices.firstOrNull {
        it.isOnline && sameDevice(it, pairingResult)
      }
      if (device != null) {
        return createAdbOnlineDevice(device, rem)
      }

      if (rem.remainingUnits <= 0) {
        throw AdbCommandException("Device did not connect within specified timeout", -1, emptyList())
      }

      // Put thread back to sleep for a little bit to avoid busy loop
      Thread.sleep(50)
    }
  }

  private fun createAdbOnlineDevice(device: IDevice, rem: TimeoutRemainder): AdbOnlineDevice {
    // Force fetching all properties by fetching one
    val futureProp = device.getSystemProperty(IDevice.PROP_DEVICE_MODEL)
    futureProp.get(rem.remainingUnits, TimeUnit.MILLISECONDS)
    if (!device.arePropertiesSet()) {
      throw AdbCommandException("Device did not connect within specified timeout", -1, emptyList())
    }

    // Return initialized device
    @Suppress("DEPRECATION")
    return AdbOnlineDevice(device.serialNumber, device.properties)
  }

  private fun sameDevice(device: IDevice, pairingResult: PairingResult): Boolean {
    return sameIpAddress(device, pairingResult.ipAddress) ||
           sameMdnsService(device, pairingResult.mdnsServiceId)
  }

  private fun sameMdnsService(device: IDevice, mdnsServiceId: String): Boolean {
    return device.serialNumber.startsWith(mdnsServiceId)
  }

  private fun sameIpAddress(device: IDevice, ipAddress: InetAddress): Boolean {
    // Note: pre-release versions of ADB used to set IP:PORT as the serial number of the device
    val comps = device.serialNumber.split(":")
    return comps.size == 2 &&
           comps[0] == ipAddress.hostAddress
  }
}
