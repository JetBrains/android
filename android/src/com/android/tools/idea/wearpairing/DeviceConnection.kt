/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull


private const val REFRESH_CONNECTION_COMMAND =
  "am broadcast -a com.google.android.gms.wearable.EMULATOR --es operation refresh-emulator-connection"
private const val GET_PAIRING_STATUS_COMMAND =
  "am broadcast -a com.google.android.gms.wearable.EMULATOR --es operation get-pairing-status"
private val LOCAL_NODE_REGEX = "Local:\\[([^\\[\\]]+)]".toRegex()
private const val GMS_PACKAGE = "com.google.android.gms"

object DeviceConnection
private val LOG get() = logger<DeviceConnection>()

suspend fun IDevice.executeShellCommand(cmd: String) {
  withContext(AndroidDispatchers.ioThread) {
    runCatching {
      executeShellCommand(cmd, NullOutputReceiver())
    }
  }
}

suspend fun IDevice.runShellCommand(cmd: String): String = withContext(AndroidDispatchers.ioThread) {
  val outputReceiver = CollectingOutputReceiver()
  runCatching {
    executeShellCommand(cmd, outputReceiver)
  }
  outputReceiver.output.trim()
}

suspend fun IDevice.loadNodeID(): String {
  return if (hasPairingFeature(PairingFeature.GET_PAIRING_STATUS)) {
    LOCAL_NODE_REGEX.find(runShellCommand(GET_PAIRING_STATUS_COMMAND))?.groupValues?.get(1) ?: ""
  }
  else {
    val localIdPattern = "local: "
    val output = runShellCommand("dumpsys activity service WearableService | grep '$localIdPattern'")
    output.replace(localIdPattern, "").trim()
  }
}

suspend fun IDevice.loadCloudNetworkID(ignoreNullOutput: Boolean = true): String {
  val cloudNetworkIdPattern = "cloud network id: "
  val output = runShellCommand("dumpsys activity service WearableService | grep '$cloudNetworkIdPattern'")
  return output.replace(cloudNetworkIdPattern, "").run {
    // The Wear Device may have a "null" cloud ID until ADB forward is established and a properly setup phone connects to it.
    if (ignoreNullOutput) replace("null", "") else this
  }.trim()
}

suspend fun IDevice.retrieveUpTime(): Double {
  runCatching {
    val uptimeRes = runShellCommand("cat /proc/uptime")
    return uptimeRes.split(' ').firstOrNull()?.toDoubleOrNull() ?: 0.0
  }
  return 0.0
}

suspend fun IDevice.refreshEmulatorConnection() {
  if (hasPairingFeature(PairingFeature.REFRESH_EMULATOR_CONNECTION)) {
    runShellCommand(REFRESH_CONNECTION_COMMAND)
  }
  else {
    restartGmsCore()
  }
}

suspend fun IDevice.isCompanionAppInstalled(companionAppId: String): Boolean {
  val output = runShellCommand("dumpsys package $companionAppId | grep versionName")
  return output.contains("versionName=")
}

suspend fun checkDevicesPaired(phoneDevice: IDevice, wearDevice: IDevice): Boolean {
  val phoneDeviceID = phoneDevice.loadNodeID()
  if (phoneDeviceID.isNotEmpty()) {
    val wearPattern = "connection to peer node: $phoneDeviceID"
    val wearOutput = wearDevice.runShellCommand("dumpsys activity service WearableService | grep '$wearPattern'")
    return wearOutput.isNotBlank()
  }
  return false
}

private suspend fun IDevice.killGmsCore() {
  runCatching {
    val uptime = retrieveUpTime()
    // Killing gmsCore during cold boot will hang booting for a while, so skip it
    if (uptime > 120.0) {
      LOG.warn("[$name] Killing Google Play Services")
      executeShellCommand("am force-stop $GMS_PACKAGE")
    }
    else {
      LOG.warn("[$name] Skip killing Google Play Services (uptime = $uptime)")
    }
  }
}

private suspend fun IDevice.restartGmsCore() {
  killGmsCore()

  LOG.warn("[$name] Wait for Google Play Services re-start")
  val res = withTimeoutOrNull(30_000) {
    while (loadNodeID().isEmpty()) {
      // Restart in case it doesn't restart automatically
      executeShellCommand("am broadcast -a $GMS_PACKAGE.INITIALIZE")
      delay(1_000)
    }
    true
  }
  when (res) {
    true -> LOG.warn("[$name] Google Play Services started")
    else -> LOG.warn("[$name] Google Play Services never started")
  }
}