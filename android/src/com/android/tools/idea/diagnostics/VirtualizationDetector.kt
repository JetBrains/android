/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import com.google.wireless.android.sdk.stats.VirtualizationEvent.ContainerType
import com.google.wireless.android.sdk.stats.VirtualizationEvent.VmType
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class VirtualizationDetector(private val handler: VirtualizationHandler) {

  fun detectVirtualization() {
    CoroutineScope(Dispatchers.Default).launch {
      val vmState = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { detectVM() } ?: VmType.VM_ERROR_TIMEOUT
      val containerState = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { detectContainer() } ?: ContainerType.CONTAINER_ERROR_TIMEOUT

      handler.onVirtualizationDetected(vmState, containerState)
    }
  }

  private suspend fun detectVM(): VmType {
    return when {
      SystemInfo.isLinux -> detectLinuxVM()
      SystemInfo.isMac -> detectMacOSVirtualization()
      else -> VmType.VM_NONE
    }
  }

  private suspend fun detectContainer(): ContainerType {
    return when {
      SystemInfo.isLinux -> detectLinuxContainer()
      else -> ContainerType.CONTAINER_NONE
    }
  }

  private suspend fun detectLinuxVM(): VmType {
    val result = runCommand("$SYSTEMD_DETECT_VIRT_COMMAND --vm")
    return when (result) {
      "kvm" -> VmType.VM_KVM
      "qnx" -> VmType.VM_QNX
      "amazon" -> VmType.VM_AMAZON
      "vmware" -> VmType.VM_VMWARE
      "microsoft" -> VmType.VM_MICROSOFT
      "apple" -> VmType.VM_APPLE
      "google" -> VmType.VM_GOOGLE
      "oracle" -> VmType.VM_ORACLE
      "powervm" -> VmType.VM_POWERVM
      "uml" -> VmType.VM_UML
      "none" -> VmType.VM_NONE
      else -> if (result.isNotBlank()) VmType.VM_OTHER else VmType.VM_ERROR_CANNOT_DETECT
    }
  }

  private suspend fun detectLinuxContainer(): ContainerType {
    val result = runCommand("$SYSTEMD_DETECT_VIRT_COMMAND --container")
    return when (result) {
      "docker" -> ContainerType.CONTAINER_DOCKER
      "wsl" -> ContainerType.CONTAINER_WSL
      "none" -> ContainerType.CONTAINER_NONE
      else -> if (result.isNotBlank()) ContainerType.CONTAINER_OTHER else ContainerType.CONTAINER_ERROR_CANNOT_DETECT
    }
  }

  private suspend fun detectMacOSVirtualization(): VmType {
    return withContext(Dispatchers.IO) {
      try {
        val process = ProcessBuilder(MACOS_SYSCTL_COMMAND, "-a")
          .redirectErrorStream(true)
          .start()

        if (!process.waitFor(2, TimeUnit.SECONDS)) {
          process.destroy()
          return@withContext VmType.VM_ERROR_TIMEOUT
        }

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
          val result = reader.lineSequence().find { it.contains("VMM") }
          if (result != null) VmType.VM_APPLE else VmType.VM_NONE
        }
      } catch (e: Exception) {
        println(e)
        VmType.VM_ERROR_CANNOT_DETECT

      }
    }
  }

  private suspend fun runCommand(command: String): String {
    return withContext(Dispatchers.IO) {
      try {
        val process = ProcessBuilder(command.split(" "))
          .redirectErrorStream(true)
          .start()

        if (!process.waitFor(2, TimeUnit.SECONDS)) {
          process.destroy()
          return@withContext "error:timeout"
        }

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
          reader.readLine()?.takeIf { it.isNotBlank() } ?: "none"
        }
      } catch (e: Exception) {
        "error:cannot-detect"
      }
    }
  }

  companion object {
    private const val MACOS_SYSCTL_COMMAND = "/usr/sbin/sysctl"
    private const val SYSTEMD_DETECT_VIRT_COMMAND = "/usr/bin/systemd-detect-virt"
    private const val COMMAND_TIMEOUT_MS = 5000L
  }
}
