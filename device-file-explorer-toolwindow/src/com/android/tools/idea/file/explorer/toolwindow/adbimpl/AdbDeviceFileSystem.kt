/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.file.explorer.toolwindow.adbimpl

import com.android.adblib.ConnectedDevice
import com.android.adblib.deviceInfo
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.ddmlib.FileListingService
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileEntry
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystem
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceState
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

class AdbDeviceFileSystem(
  val deviceHandle: DeviceHandle,
  val device: ConnectedDevice,
  edtExecutor: Executor,
  val dispatcher: CoroutineDispatcher
) : DeviceFileSystem {

  override val scope: CoroutineScope = device.scope + SupervisorJob(device.scope.coroutineContext.job)

  private val myEdtExecutor = FutureCallbackExecutor(edtExecutor)
  val capabilities = AdbDeviceCapabilities(scope + dispatcher, deviceHandle.state.properties.title(), this.device)
  val adbFileListing = AdbFileListing(this.device, capabilities, dispatcher)
  val adbFileOperations = AdbFileOperations(this.device, capabilities, dispatcher)
  val adbFileTransfer = AdbFileTransfer(this.device, adbFileOperations, myEdtExecutor, dispatcher)

  override val name = deviceHandle.state.properties.title()

  override val deviceSerialNumber: String
    get() = device.serialNumber

  private fun com.android.adblib.DeviceState.toDeviceState() =
    when (this) {
      com.android.adblib.DeviceState.ONLINE -> DeviceState.ONLINE
      com.android.adblib.DeviceState.OFFLINE -> DeviceState.OFFLINE
      com.android.adblib.DeviceState.UNAUTHORIZED -> DeviceState.UNAUTHORIZED
      com.android.adblib.DeviceState.DISCONNECTED -> DeviceState.DISCONNECTED
      com.android.adblib.DeviceState.BOOTLOADER -> DeviceState.BOOTLOADER
      com.android.adblib.DeviceState.RECOVERY -> DeviceState.RECOVERY
      com.android.adblib.DeviceState.SIDELOAD -> DeviceState.SIDELOAD
      else -> DeviceState.DISCONNECTED
    }

  override val deviceStateFlow: StateFlow<DeviceState> =
    device.deviceInfoFlow
      .map { it.deviceState.toDeviceState() }
      .stateIn(
        scope,
        SharingStarted.Eagerly,
        device.deviceInfo.deviceState.toDeviceState()
      )

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
