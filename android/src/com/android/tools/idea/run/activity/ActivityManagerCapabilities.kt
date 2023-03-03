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
import com.android.adblib.deviceCache
import com.android.adblib.shellCommand
import com.android.adblib.utils.ByteArrayShellCollector
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.server.am.Capabilities
import com.android.tools.idea.adblib.AdbLibService
import com.google.protobuf.InvalidProtocolBufferException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Duration


private val activityManagerCapabilitiesKey = CoroutineScopeCache.Key<ActivityManagerCapabilities.CapabilitiesResult>(
  "ActivityManagerCapabilities")

class ActivityManagerCapabilities(val project: Project) {

  companion object {
    @JvmStatic
    @WorkerThread
    fun suspendSupported(project: Project, device: IDevice): Boolean {
      return runBlocking {
        withTimeout(Duration.ofSeconds(20).toMillis()) {
          // This value comes from
          // frameworks/base/services/core/java/com/android/server/am/ActivityManagerShellCommand.java
          ActivityManagerCapabilities(project).checkCapability(device, "start.suspend")
        }
      }
    }
  }

  private suspend fun checkCapability(device: IDevice, capability: String): Boolean {
    val serial = device.serialNumber
    val deviceCache = AdbLibService.getSession(project).deviceCache(serial)
    val result = deviceCache.getOrPut(activityManagerCapabilitiesKey) { CapabilitiesResult() }
    val caps = result.mutex.withLock {
      result.capabilities ?: retrieveCapabilities(serial).also {
        result.capabilities = it
      }
    }
    return caps.contains(capability)
  }

  private suspend fun retrieveCapabilities(serialNumber: String): HashSet<String> {
    val result = runCatching {
      AdbLibService.getSession(project).deviceServices
        .shellCommand(DeviceSelector.fromSerialNumber(serialNumber), "am capabilities --protobuf")
        .withCollector(ByteArrayShellCollector())
        .executeAsSingleOutput { it }
    }.getOrElse { throwable ->
      throw Exception("Error retrieving capabilities from the device $serialNumber", throwable)
    }

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

  class CapabilitiesResult(
    val mutex: Mutex = Mutex(),
    var capabilities: HashSet<String>? = null
  )

}