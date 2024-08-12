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

import com.android.adblib.ServerStatus
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.TimeoutRemainder
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AdbServiceWrapperImpl(
  private val project: Project,
  private val nanoTimeProvider: TimeoutRemainder.SystemNanoTimeProvider,
) : AdbServiceWrapper {
  private val ADB_TIMEOUT_MILLIS = 30_000L
  private val LOG = thisLogger()

  override suspend fun executeCommand(args: List<String>, stdin: String): AdbCommandResult {
    val adbFile = getAdbLocation()
    // Execute ADB command, capturing output and exit value
    val stdinStream = stdin.byteInputStream()
    val stdoutStream = ByteArrayOutputStream()
    val stderrStream = ByteArrayOutputStream()

    val exitValue =
      withContext(Dispatchers.IO) {
        ExternalCommand(adbFile.absolutePath)
          .execute(
            args,
            stdinStream,
            stdoutStream,
            stderrStream,
            ADB_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS,
          )
      }
    val processOutput = ProcessOutput()
    processOutput.appendStdout(stdoutStream.toString("UTF-8"))
    processOutput.appendStderr(stderrStream.toString("UTF-8"))
    return AdbCommandResult(exitValue, processOutput.stdoutLines, processOutput.stderrLines)
  }

  override suspend fun waitForOnlineDevice(pairingResult: PairingResult): AdbOnlineDevice =
    withContext(Dispatchers.IO) {
      val adbFile = getAdbLocation()
      val adb = AdbService.getInstance().getDebugBridge(adbFile).await()
      waitForDevice(adb, pairingResult)
    }

  override suspend fun getServerStatus(): ServerStatus {
    throw NotImplementedError("DDMLib does not implement server-status")
  }

  private suspend fun getAdbLocation(): File =
    // Use the I/O thread just in case we do I/O in the future (although currently there is none)
    withContext(Dispatchers.IO) {
      AdbFileProvider.fromProject(project).get()
        ?: throw IllegalStateException("The path to the ADB command is not available")
    }

  private suspend fun waitForDevice(
    debugBridge: AndroidDebugBridge,
    pairingResult: PairingResult,
  ): AdbOnlineDevice {
    val rem = TimeoutRemainder(nanoTimeProvider, ADB_DEVICE_CONNECT_MILLIS, TimeUnit.MILLISECONDS)
    while (true) {
      val device = debugBridge.devices.firstOrNull { it.isOnline && sameDevice(it, pairingResult) }
      if (device != null) {
        return createAdbOnlineDevice(device, rem)
      }

      if (rem.remainingNanos <= 0) {
        throw AdbCommandException(
          "Device did not connect within specified timeout",
          -1,
          emptyList(),
        )
      }

      // Put thread back to sleep for a little bit to avoid busy loop
      delay(Duration.ofMillis(50))
    }
  }

  private suspend fun createAdbOnlineDevice(
    device: IDevice,
    rem: TimeoutRemainder,
  ): AdbOnlineDevice {
    // Force fetching all properties by fetching one
    withTimeoutOrNull(rem.getRemainingUnits(TimeUnit.MILLISECONDS)) {
      device.getSystemProperty(IDevice.PROP_DEVICE_MODEL).await()
    }
    // Ignore timeout above -- just check here if we got the properties
    if (!device.arePropertiesSet()) {
      throw AdbCommandException("Device did not connect within specified timeout", -1, emptyList())
    }

    // Return initialized device
    @Suppress("DEPRECATION") return AdbOnlineDevice(device.serialNumber, device.properties)
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
    return comps.size == 2 && comps[0] == ipAddress.hostAddress
  }
}
