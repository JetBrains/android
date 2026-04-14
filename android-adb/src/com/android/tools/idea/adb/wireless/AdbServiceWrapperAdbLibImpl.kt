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

import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbFeatures
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.MdnsServices
import com.android.adblib.ServerStatus
import com.android.adblib.deviceProperties
import com.android.adblib.trackDevices
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.LineSeparator
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Any non-zero code to simulate a failing ADB command call */
private const val ADB_FAILED_COMMAND_ERROR_CODE = 5

class AdbServiceWrapperAdbLibImpl(private val project: Project) : AdbServiceWrapper {

  private val log = logger<AdbServiceWrapperAdbLibImpl>()

  override suspend fun executeCommand(args: List<String>, stdin: String): AdbCommandResult {
    return withContext(Dispatchers.IO) {
      if (args == listOf("mdns", "check")) {
        mdnsCheck()
      } else if (args == listOf("mdns", "services")) {
        mdnsServices()
      } else if (args[0] == "pair") {
        mdnsPair(args[1], stdin)
      } else {
        throw IllegalArgumentException("Unsupported ADB command")
      }
    }
  }

  override fun trackMdnsServices(): Flow<MdnsServices> {
    return AdbLibService.getSession(project).hostServices.trackMdnsServices()
  }

  private suspend fun mdnsCheck(): AdbCommandResult {
    val hostServices = AdbLibService.getSession(project).hostServices
    return try {
      val result = hostServices.mdnsCheck()
      AdbCommandResult(0, listOf(result.rawText), listOf())
    } catch (e: AdbFailResponseException) {
      AdbCommandResult(ADB_FAILED_COMMAND_ERROR_CODE, listOf(), listOf(e.failMessage))
    }
  }

  private suspend fun mdnsServices(): AdbCommandResult {
    val hostServices = AdbLibService.getSession(project).hostServices
    return try {
      val result = hostServices.mdnsServices()
      result.errors.forEach { errorLine ->
        log.error("'${errorLine.message}' error while parsing 'mdns services' output line: '${errorLine.rawLineText}'")
      }

      // Recreate stdout from parsed result
      // See format of output at
      // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=1948
      // and
      // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/transport_mdns.cpp;l=302;drc=3a52886262ae22477a7d8ffb12adba64daf6aafa
      val stdout = listOf("List of discovered mdns services") + result.map { "${it.instanceName}\t${it.serviceName}\t${it.deviceAddress}" }
      AdbCommandResult(0, stdout, listOf())
    } catch (e: AdbFailResponseException) {
      AdbCommandResult(ADB_FAILED_COMMAND_ERROR_CODE, listOf(), listOf(e.failMessage))
    }
  }

  private suspend fun mdnsPair(deviceAddress: String, stdin: String): AdbCommandResult {
    val hostServices = AdbLibService.getSession(project).hostServices
    return try {
      // stdin contains the password followed by a '\n' to simulate the ENTER key, so we remove it
      val password = stdin.removeSuffix(LineSeparator.getSystemLineSeparator().separatorString)
      val result = hostServices.pair(DeviceAddress(deviceAddress), password)
      AdbCommandResult(0, listOf(result.rawText), listOf())
    } catch (e: AdbFailResponseException) {
      AdbCommandResult(ADB_FAILED_COMMAND_ERROR_CODE, listOf(), listOf(e.failMessage))
    }
  }

  override suspend fun waitForOnlineDevice(pairingResult: PairingResult): AdbOnlineDevice {
    val ipAddress = pairingResult.ipAddress.hostAddress
    val mdnsId = pairingResult.mdnsServiceId

    return withTimeoutOrNull(ADB_DEVICE_CONNECT_MILLIS.milliseconds) {
      val session = AdbLibService.getSession(project)
      session
        .trackDevices()
        .mapNotNull { deviceList ->
          deviceList.firstOrNull { device ->
            device.deviceState == DeviceState.ONLINE &&
              ((mdnsId.isNotEmpty() && device.serialNumber.startsWith(mdnsId)) ||
                (device.serialNumber.startsWith(ipAddress) &&
                  (device.serialNumber.length == ipAddress.length || device.serialNumber[ipAddress.length] == ':')))
          }
        }
        .first()
        .let { createAdbOnlineDevice(it) }
    } ?: throw AdbCommandException("Device did not connect within specified timeout", -1, emptyList())
  }

  override suspend fun getServerStatus(): ServerStatus {
    val session = AdbLibService.getSession(project)
    if (!session.hostServices.hostFeatures().contains(AdbFeatures.SERVER_STATUS)) {
      return ServerStatus()
    }

    return session.hostServices.serverStatus()
  }

  override suspend fun getHostFeatures(): List<String> {
    val session = AdbLibService.getSession(project)
    return session.hostServices.hostFeatures()
  }

  private suspend fun createAdbOnlineDevice(device: DeviceInfo): AdbOnlineDevice {
    val properties = getDeviceProperties(device)
    return AdbOnlineDevice(device.serialNumber, properties)
  }

  private suspend fun getDeviceProperties(device: DeviceInfo): Map<String, String> {
    val deviceServices = AdbLibService.getSession(project).deviceServices
    val props = deviceServices.deviceProperties(DeviceSelector.fromSerialNumber(device.serialNumber)).all()
    return props.associate { it.name to it.value }
  }
}
