/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.activity

import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.device
import com.android.adblib.shellCommand
import com.android.adblib.utils.ByteArrayShellCollector
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.server.am.Capabilities
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.google.protobuf.InvalidProtocolBufferException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration


private val activityManagerCapabilitiesKey = CoroutineScopeCache.Key<HashSet<String>>(
  "ActivityManagerCapabilities")

class ActivityManagerCapabilities(val project: Project) {

  companion object {
    @JvmStatic
    @WorkerThread
    fun suspendSupported(project: Project, device: IDevice): Boolean {
      return runBlocking {
        withTimeoutOrNull(Duration.ofSeconds(20).toMillis()) {
          // This value comes from
          // frameworks/base/services/core/java/com/android/server/am/ActivityManagerShellCommand.java
          ActivityManagerCapabilities(project).checkCapability(device, "start.suspend")
        }?: throw AndroidExecutionException("OPERATION_TIMEOUT", "Unable to retrieve suspend capability")
      }
    }
  }

  private suspend fun checkCapability(device: IDevice, capability: String): Boolean {
    val caps = kotlin.runCatching {
      val deviceSelector = DeviceSelector.fromSerialNumber(device.serialNumber)
      val connectedDevice = AdbLibService.getSession(project).connectedDevicesTracker.device(deviceSelector)

      // Use device cache if available
      connectedDevice?.cache?.getOrPutSuspending(activityManagerCapabilitiesKey) { retrieveCapabilities(deviceSelector) }
      ?: retrieveCapabilities(deviceSelector)
    }.getOrElse { throwable ->
      throw Exception("Error retrieving capabilities from the device ${device.serialNumber}", throwable)
    }
    return caps.contains(capability)
  }

  private suspend fun retrieveCapabilities(deviceSelector: DeviceSelector): HashSet<String> {
    val result = AdbLibService.getSession(project).deviceServices
      .shellCommand(deviceSelector, "am capabilities --protobuf")
      .withCollector(ByteArrayShellCollector())
      .executeAsSingleOutput { it }

    val protoCapabilities = try {
      Capabilities.parseFrom(result.stdout)
    }
    catch (e: InvalidProtocolBufferException) {
      // If "am" does not support "capabilities" command it returns something like
      // "Unknown command: capabilities". Failing to parse means the device has
      // no know capabilities.
      return HashSet()
    }

    val capabilities: HashSet<String> = HashSet()
    for (capability in protoCapabilities.valuesList) {
      capabilities.add(capability.name)
    }
    return capabilities
  }
}