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
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Converts the given IDevice into an AndroidDevice
 */
@WorkerThread
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
    executeShellCommandAndProcessOutput(device, "cat /proc/meminfo").let { output ->
      output.map {
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
      }.filterNotNull().firstOrNull()?.let {
        additionalInfo["RAM"] = it
      }
    }

    executeShellCommandAndProcessOutput(device, "cat /proc/cpuinfo").let { output ->
      val cpus = output.mapNotNull {
        val (key, value) = it.split(':', ignoreCase = true, limit = 2) + listOf("", "")
        if (key.trim() == "model name") {
          value.trim()
        }
        else {
          null
        }
      }.toSet()

      if (cpus.isNotEmpty()) {
        additionalInfo["Processor"] = cpus.joinToString("\n")
      }
    }

    executeShellCommandAndProcessOutput(device, "getprop ro.product.manufacturer").let {
      it.getOrNull(0)?.let { additionalInfo["Manufacturer"] = it }
    }
    executeShellCommandAndProcessOutput(device, "getprop ro.product.model").let { it.getOrNull(0)?.let { additionalInfo["Model"] = it } }
  }
}

/**
 * Executes a given shell command on a given device and returns output.
 */
@WorkerThread
private fun executeShellCommandAndProcessOutput(device: IDevice, command: String): MutableList<String> {
  val receiver = object : MultiLineReceiver() {
    val output = mutableListOf<String>()
    override fun isCancelled() = false

    override fun processNewLines(lines: Array<out String>) {
      output.addAll(lines)
    }
  }
  device.executeShellCommand(command, receiver, 10, TimeUnit.SECONDS)
  return receiver.output
}