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
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val REFRESH_CONNECTION_COMMAND =
  "am broadcast -a com.google.android.gms.wearable.EMULATOR --es operation refresh-emulator-connection"
private const val GET_PAIRING_STATUS_COMMAND =
  "am broadcast -a com.google.android.gms.wearable.EMULATOR --es operation get-pairing-status"
private val LOCAL_NODE_REGEX = "Local:\\[([^\\[\\]]+)]".toRegex()
private val PEER_NODE_REGEX = "Peer:\\[([^\\[\\],]+),(true|false),(true|false)]".toRegex()
private const val GMS_PACKAGE = "com.google.android.gms"

object DeviceConnection

private val LOG
  get() = logger<DeviceConnection>()

suspend fun IDevice.executeShellCommand(cmd: String) {
  withContext(Dispatchers.IO) { runCatching { executeShellCommand(cmd, NullOutputReceiver()) } }
}

suspend fun IDevice.runShellCommand(cmd: String): String =
  withContext(Dispatchers.IO) {
    val outputReceiver = CollectingOutputReceiver()
    runCatching { executeShellCommand(cmd, outputReceiver) }
    outputReceiver.output.trim()
  }

private suspend fun IDevice.localNodeFromPairingStatus(): String? =
  LOCAL_NODE_REGEX.find(runShellCommand(GET_PAIRING_STATUS_COMMAND))?.groupValues?.get(1)

suspend fun IDevice.isPairingStatusAvailable(): Boolean = localNodeFromPairingStatus() != null

suspend fun IDevice.loadNodeID(): String {
  if (hasPairingFeature(PairingFeature.GET_PAIRING_STATUS)) {
    localNodeFromPairingStatus()?.let {
      return it
    }
  }
  val localIdPattern = "local: "
  val output = runShellCommand("dumpsys activity service WearableService | grep '$localIdPattern'")
  return output.replace(localIdPattern, "").trim()
}

suspend fun IDevice.loadCloudNetworkID(ignoreNullOutput: Boolean = true): String {
  val cloudNetworkIdPattern = "cloud network id: "
  val output =
    runShellCommand("dumpsys activity service WearableService | grep '$cloudNetworkIdPattern'")
  return output
    .replace(cloudNetworkIdPattern, "")
    .run {
      // The Wear Device may have a "null" cloud ID until ADB forward is established and a properly
      // setup phone connects to it.
      if (ignoreNullOutput) replace("null", "") else this
    }
    .trim()
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
  } else {
    restartGmsCore()
  }
}

suspend fun IDevice.isCompanionAppInstalled(companionAppId: String): Boolean {
  val output = runShellCommand("dumpsys package $companionAppId | grep versionName")
  return output.contains("versionName=")
}

data class PairingStatus(val nodeId: String?, val connected: Boolean, val enabled: Boolean)

private suspend fun IDevice.getPairingStatus(peerNodeId: String): Pair<String?, PairingStatus?> {
  val (localNodeId, pairingStatuses) = getPairingStatus()
  return Pair(
    localNodeId,
    pairingStatuses.firstOrNull { peerNodeId.equals(it.nodeId, ignoreCase = true) },
  )
}

suspend fun IDevice.getPairingStatus(): Pair<String?, List<PairingStatus>> {
  val broadcastResult = runShellCommand(GET_PAIRING_STATUS_COMMAND)
  var localNodeId: String? = null
  val peerStatus =
    broadcastResult
      .lines()
      .also { if (it.size > 1) localNodeId = LOCAL_NODE_REGEX.find(it[1])?.groupValues?.get(1) }
      .takeIf { it.size > 2 }
      ?.let { it.subList(2, it.size) }
      ?.mapNotNull { PEER_NODE_REGEX.find(it)?.groupValues }
      ?.filter { it.size >= 4 }
      ?.map {
        PairingStatus(
          if ("null".equals(it[1], ignoreCase = true)) null else it[1],
          it[2].toBoolean(),
          it[3].toBoolean(),
        )
      }
  if (localNodeId == null) LOG.error("Unexpected pairing status: $broadcastResult")
  return Pair(localNodeId, peerStatus.orEmpty())
}

suspend fun checkDevicesPaired(phoneDevice: IDevice, wearDevice: IDevice): Boolean {
  if (phoneDevice.hasPairingFeature(PairingFeature.GET_PAIRING_STATUS)) {
    // TODO: We need additional states to differentiate between the cases where the nodeId matches
    //  but it's either not enabled or is not connected
    val (localNodeId, pairingStatus) = phoneDevice.getPairingStatus(wearDevice.loadNodeID())
    if (localNodeId != null) {
      return pairingStatus?.takeIf { it.enabled && it.connected } != null
    }
  }
  val phoneDeviceID = phoneDevice.loadNodeID()
  if (phoneDeviceID.isNotEmpty()) {
    val wearPattern = "connection to peer node: $phoneDeviceID"
    val wearOutput =
      wearDevice.runShellCommand("dumpsys activity service WearableService | grep '$wearPattern'")
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
    } else {
      LOG.warn("[$name] Skip killing Google Play Services (uptime = $uptime)")
    }
  }
}

private suspend fun IDevice.restartGmsCore() {
  killGmsCore()

  LOG.warn("[$name] Wait for Google Play Services re-start")
  val res =
    withTimeoutOrNull(30_000) {
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
