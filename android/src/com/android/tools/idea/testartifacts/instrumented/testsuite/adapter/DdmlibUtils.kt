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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.androidCoroutineExceptionHandler
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Converts the given IDevice into an AndroidDevice
 */
fun convertIDeviceToAndroidDevice(device: IDevice): AndroidDevice {
  return AndroidDevice(device.serialNumber,
                       device.avdName ?: "",
                       device.avdName ?: "",
                       if (device.isEmulator) {
                         AndroidDeviceType.LOCAL_EMULATOR
                       }
                       else {
                         AndroidDeviceType.LOCAL_PHYSICAL_DEVICE
                       },
                       device.version,
                       Collections.synchronizedMap(LinkedHashMap())).apply {
    additionalInfo["SerialNumber"] = device.serialNumber
    CoroutineScope(AndroidDispatchers.workerThread + androidCoroutineExceptionHandler).launch {
      executeShellCommandSync(device, "cat /proc/meminfo") { output ->
        output.lineSequence().map {
          val (key, value) = it.split(':', ignoreCase = true, limit = 2) + listOf("", "")
          if (key.trim() == "MemTotal") {
            val (ramSize, unit) = value.trim().split(' ', ignoreCase = true, limit = 2)
            val ramSizeFloat = ramSize.toFloatOrNull() ?: return@map null
            when (unit) {
              "kB" -> String.format("%.1f GB", ramSizeFloat / 1000 / 1000)
              else -> null
            }
          }
          else {
            null
          }
        }.filterNotNull().firstOrNull()
      }?.let { additionalInfo["RAM"] = it }

      executeShellCommandSync(device, "cat /proc/cpuinfo") { output ->
        val cpus = output.lineSequence().map {
          val (key, value) = it.split(':', ignoreCase = true, limit = 2) + listOf("", "")
          if (key.trim() == "model name") {
            value.trim()
          }
          else {
            null
          }
        }.filterNotNull().toSet()
        if (cpus.isEmpty()) {
          null
        }
        else {
          cpus.joinToString("\n")
        }
      }?.let { additionalInfo["Processor"] = it }

      executeShellCommandSync(device, "getprop ro.product.manufacturer")?.let { additionalInfo["Manufacturer"] = it }
      executeShellCommandSync(device, "getprop ro.product.model")?.let { additionalInfo["Model"] = it }
    }
  }
}

/**
 * Executes a given shell command on a given device. This function blocks caller
 * until the command finishes or times out and returns output in string.
 *
 * @param device a target device to run a command
 * @param command a command to be executed
 * @param postProcessOutput a function which post processes the command output
 */
@WorkerThread
private fun executeShellCommandSync(device: IDevice, command: String,
                                    postProcessOutput: (output: String) -> String? = { it }): String? {
  val latch = CountDownLatch(1)
  val receiver = CollectingOutputReceiver(latch)
  device.executeShellCommand(command, receiver, 10, TimeUnit.SECONDS)
  latch.await(10, TimeUnit.SECONDS)
  return postProcessOutput(receiver.output)
}