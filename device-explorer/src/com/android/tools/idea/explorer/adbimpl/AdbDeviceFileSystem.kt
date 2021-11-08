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

import com.android.tools.idea.explorer.fs.DeviceFileEntry.entries
import com.android.tools.idea.explorer.fs.DeviceFileEntry.name
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry.fullPath
import com.android.ddmlib.IDevice
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.explorer.adbimpl.AdbFileListing
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import com.android.tools.idea.explorer.adbimpl.AdbFileTransfer
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDefaultFileEntry
import com.android.ddmlib.FileListingService
import java.lang.IllegalArgumentException
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDirectFileEntry
import com.android.tools.idea.explorer.fs.DeviceState
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.util.text.StringUtil
import java.util.concurrent.Executor

class AdbDeviceFileSystem(device: IDevice, edtExecutor: Executor, taskExecutor: Executor) : DeviceFileSystem {
  val device: IDevice
  val capabilities: AdbDeviceCapabilities
  val adbFileListing: AdbFileListing
  val adbFileOperations: AdbFileOperations
  val adbFileTransfer: AdbFileTransfer
  private val myEdtExecutor: FutureCallbackExecutor
  val taskExecutor: FutureCallbackExecutor
  fun isDevice(device: IDevice?): Boolean {
    return this.device == device
  }

  override val name: String
    get() = device.name
  override val deviceSerialNumber: String
    get() = device.serialNumber
  override val deviceState: DeviceState
    get() {
      val state = device.state ?: return DeviceState.DISCONNECTED
      return when (state) {
        IDevice.DeviceState.ONLINE -> DeviceState.ONLINE
        IDevice.DeviceState.OFFLINE -> DeviceState.OFFLINE
        IDevice.DeviceState.UNAUTHORIZED -> DeviceState.UNAUTHORIZED
        IDevice.DeviceState.DISCONNECTED -> DeviceState.DISCONNECTED
        IDevice.DeviceState.BOOTLOADER -> DeviceState.BOOTLOADER
        IDevice.DeviceState.RECOVERY -> DeviceState.RECOVERY
        IDevice.DeviceState.SIDELOAD -> DeviceState.SIDELOAD
        else -> DeviceState.DISCONNECTED
      }
    }
  override val rootDirectory: ListenableFuture<DeviceFileEntry>
    get() = taskExecutor.transform(adbFileListing.root) { entry: AdbFileListingEntry? ->
      assert(entry != null)
      AdbDeviceDefaultFileEntry(this, entry!!, null)
    }

  override fun getEntry(path: String): ListenableFuture<DeviceFileEntry?> {
    val resultFuture = SettableFuture.create<DeviceFileEntry?>()
    val currentDir = rootDirectory
    taskExecutor.addCallback(currentDir, object : FutureCallback<DeviceFileEntry?> {
      override fun onSuccess(result: DeviceFileEntry?) {
        assert(result != null)
        if (StringUtil.isEmpty(path) || StringUtil.equals(path, FileListingService.FILE_SEPARATOR)) {
          resultFuture.set(result)
          return
        }
        val pathSegments = path.substring(1).split(FileListingService.FILE_SEPARATOR.toRegex()).toTypedArray()
        resolvePathSegments(resultFuture, result!!, pathSegments, 0)
      }

      override fun onFailure(t: Throwable) {
        resultFuture.setException(t)
      }
    })
    return resultFuture
  }

  private fun resolvePathSegments(
    future: SettableFuture<DeviceFileEntry?>,
    currentEntry: DeviceFileEntry,
    segments: Array<String>,
    segmentIndex: Int
  ) {
    if (segmentIndex >= segments.size) {
      future.set(currentEntry)
      return
    }
    val entriesFuture = currentEntry.entries
    taskExecutor.addCallback(entriesFuture, object : FutureCallback<List<DeviceFileEntry>?> {
      override fun onSuccess(result: List<DeviceFileEntry>?) {
        assert(result != null)
        val entry = result
          .stream()
          .filter { x: DeviceFileEntry -> x.name == segments[segmentIndex] }
          .findFirst()
        if (!entry.isPresent) {
          future.setException(IllegalArgumentException("Path not found"))
        } else {
          resolvePathSegments(future, entry.get(), segments, segmentIndex + 1)
        }
      }

      override fun onFailure(t: Throwable) {
        future.setException(t)
      }
    })
  }

  fun resolveMountPoint(entry: AdbDeviceFileEntry): ListenableFuture<AdbDeviceFileEntry> {
    return taskExecutor.executeAsync {

      // Root devices or "su 0" devices don't need mount points
      if (capabilities.supportsSuRootCommand() || capabilities.isRoot) {
        return@executeAsync createDirectFileEntry(entry)
      }

      // The "/data" folder has directories where we need to use "run-as"
      if (entry.fullPath == "/data") {
        return@executeAsync AdbDeviceDataDirectoryEntry(entry)
      }
      createDirectFileEntry(entry)
    }
  }

  companion object {
    private fun createDirectFileEntry(entry: AdbDeviceFileEntry): AdbDeviceDirectFileEntry {
      return AdbDeviceDirectFileEntry(entry.myDevice, entry.myEntry, entry.parent, null)
    }
  }

  init {
    myEdtExecutor = FutureCallbackExecutor(edtExecutor)
    this.taskExecutor = FutureCallbackExecutor(taskExecutor)
    this.device = device
    capabilities = AdbDeviceCapabilities(this.device)
    adbFileListing = AdbFileListing(this.device, capabilities, this.taskExecutor)
    adbFileOperations = AdbFileOperations(this.device, capabilities, this.taskExecutor)
    adbFileTransfer = AdbFileTransfer(this.device, adbFileOperations, myEdtExecutor, this.taskExecutor)
  }
}