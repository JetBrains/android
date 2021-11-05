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
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.util.text.StringUtil
import java.util.concurrent.Executor

class AdbDeviceFileSystem(val device: IDevice, edtExecutor: Executor, taskExecutor: Executor) : DeviceFileSystem {
  private val myEdtExecutor = FutureCallbackExecutor(edtExecutor)
  val taskExecutor = FutureCallbackExecutor(taskExecutor)
  val capabilities = AdbDeviceCapabilities(this.device)
  val adbFileListing = AdbFileListing(this.device, capabilities, this.taskExecutor)
  val adbFileOperations = AdbFileOperations(this.device, capabilities, this.taskExecutor)
  val adbFileTransfer = AdbFileTransfer(this.device, adbFileOperations, myEdtExecutor, this.taskExecutor)

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

  override val rootDirectory: ListenableFuture<DeviceFileEntry>
    get() = taskExecutor.transform(adbFileListing.root) { entry: AdbFileListingEntry ->
      AdbDeviceDefaultFileEntry(this, entry, null)
    }

  override fun getEntry(path: String): ListenableFuture<DeviceFileEntry> {
    val resultFuture = SettableFuture.create<DeviceFileEntry>()
    taskExecutor.addCallback(rootDirectory, object : FutureCallback<DeviceFileEntry> {
      override fun onSuccess(result: DeviceFileEntry?) {
        checkNotNull(result)
        if (StringUtil.isEmpty(path) || StringUtil.equals(path, FileListingService.FILE_SEPARATOR)) {
          resultFuture.set(result)
          return
        }
        val pathSegments = path.substring(1).split(FileListingService.FILE_SEPARATOR.toRegex()).toTypedArray()
        resolvePathSegments(resultFuture, result, pathSegments, 0)
      }

      override fun onFailure(t: Throwable) {
        resultFuture.setException(t)
      }
    })
    return resultFuture
  }

  private fun resolvePathSegments(
    future: SettableFuture<DeviceFileEntry>,
    currentEntry: DeviceFileEntry,
    segments: Array<String>,
    segmentIndex: Int
  ) {
    if (segmentIndex >= segments.size) {
      future.set(currentEntry)
      return
    }
    val entriesFuture = currentEntry.entries
    taskExecutor.addCallback(entriesFuture, object : FutureCallback<List<DeviceFileEntry>> {
      override fun onSuccess(result: List<DeviceFileEntry>?) {
        checkNotNull(result)
        when(val entry = result.find { it.name == segments[segmentIndex] }) {
          null -> future.setException(IllegalArgumentException("Path not found"))
          else -> resolvePathSegments(future, entry, segments, segmentIndex + 1)
        }
      }

      override fun onFailure(t: Throwable) {
        future.setException(t)
      }
    })
  }

  fun resolveMountPoint(entry: AdbDeviceFileEntry): ListenableFuture<AdbDeviceFileEntry> {
    return taskExecutor.executeAsync {
      when {
        // Root devices or "su 0" devices don't need mount points
        capabilities.supportsSuRootCommand() || capabilities.isRoot -> createDirectFileEntry(entry)
        // The "/data" folder has directories where we need to use "run-as"
        entry.fullPath == "/data" -> AdbDeviceDataDirectoryEntry(entry)
        else -> createDirectFileEntry(entry)
      }
    }
  }

  companion object {
    private fun createDirectFileEntry(entry: AdbDeviceFileEntry): AdbDeviceDirectFileEntry {
      return AdbDeviceDirectFileEntry(entry.fileSystem, entry.myEntry, entry.parent, null)
    }
  }
}