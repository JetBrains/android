/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl

import com.android.ddmlib.FileListingService
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceState
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

class AdbDeviceFileSystem(val device: IDevice, edtExecutor: Executor, val dispatcher: CoroutineDispatcher) : DeviceFileSystem {
  private val myEdtExecutor = FutureCallbackExecutor(edtExecutor)
  val capabilities = AdbDeviceCapabilities(this.device)
  val adbFileListing = AdbFileListing(this.device, capabilities, dispatcher)
  val adbFileOperations = AdbFileOperations(this.device, capabilities, dispatcher)
  val adbFileTransfer = AdbFileTransfer(this.device, adbFileOperations, myEdtExecutor, dispatcher)

  fun isDevice(device: IDevice?): Boolean {
    return this.device == device
  }

  override val name: String
    get() = device.name

  override val deviceSerialNumber: String
    get() = device.serialNumber

  override val deviceState: DeviceState
    get() = when (device.state) {
      IDevice.DeviceState.ONLINE -> DeviceState.ONLINE
      IDevice.DeviceState.OFFLINE -> DeviceState.OFFLINE
      IDevice.DeviceState.UNAUTHORIZED -> DeviceState.UNAUTHORIZED
      IDevice.DeviceState.DISCONNECTED -> DeviceState.DISCONNECTED
      IDevice.DeviceState.BOOTLOADER -> DeviceState.BOOTLOADER
      IDevice.DeviceState.RECOVERY -> DeviceState.RECOVERY
      IDevice.DeviceState.SIDELOAD -> DeviceState.SIDELOAD
      else -> DeviceState.DISCONNECTED
    }

  override suspend fun rootDirectory(): DeviceFileEntry {
    return AdbDeviceDefaultFileEntry(this, adbFileListing.root, null)
  }

  override suspend fun getEntry(path: String): DeviceFileEntry {
    val root = rootDirectory()
    if (StringUtil.isEmpty(path) || StringUtil.equals(path, FileListingService.FILE_SEPARATOR)) {
      return root
    }
    val pathSegments = path.substring(1).split(FileListingService.FILE_SEPARATOR.toRegex()).toList()
    return resolvePathSegments(root, pathSegments)
  }

  private suspend fun resolvePathSegments(
    rootEntry: DeviceFileEntry,
    segments: List<String>
  ): DeviceFileEntry {
    var currentEntry = rootEntry
    for (segment in segments) {
      currentEntry = currentEntry.entries().find { it.name == segment } ?: throw IllegalArgumentException("Path not found")
    }
    return currentEntry
  }

  suspend fun resolveMountPoint(entry: AdbDeviceFileEntry): AdbDeviceFileEntry =
    withContext(dispatcher) {
      when {
        // Root devices or "su 0" devices don't need mount points
        capabilities.supportsSuRootCommand() || capabilities.isRoot() -> createDirectFileEntry(entry)
        // The "/data" folder has directories where we need to use "run-as"
        entry.fullPath == "/data" -> AdbDeviceDataDirectoryEntry(entry)
        else -> createDirectFileEntry(entry)
      }
    }

  companion object {
    private fun createDirectFileEntry(entry: AdbDeviceFileEntry): AdbDeviceDirectFileEntry {
      return AdbDeviceDirectFileEntry(entry.fileSystem, entry.myEntry, entry.parent, null)
    }
  }
}