/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.explorer.fs.DeviceFileEntry.delete
import com.android.tools.idea.explorer.fs.DeviceFileEntry.createNewFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry.createNewDirectory
import com.android.tools.idea.explorer.fs.DeviceFileEntry.isSymbolicLinkToDirectory
import com.android.tools.idea.explorer.fs.DeviceFileEntry.downloadFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry.uploadFile
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry.AdbPermissions
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry.AdbDateTime
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.adbimpl.AdbDeviceForwardingFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDirectFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDeviceDataAppDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDeviceDataDataDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDeviceDataLocalDirectoryEntry
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDevicePackageDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntryBuilder
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDefaultFileEntry
import com.android.ddmlib.SyncException
import com.android.tools.idea.adb.AdbShellCommandException
import kotlin.Throws
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.nio.file.Path

/**
 * A [AdbDeviceFileEntry] that goes through the file system mounting points (see [AdbDeviceFileSystem.resolveMountPoint])
 * for its file operations.
 */
class AdbDeviceDefaultFileEntry(
  device: AdbDeviceFileSystem,
  entry: AdbFileListingEntry,
  parent: AdbDeviceFileEntry?
) : AdbDeviceFileEntry(device, entry, parent) {
  override val entries: ListenableFuture<List<DeviceFileEntry>>
    get() {
      val futureMountPoint = myDevice.resolveMountPoint(this)
      return myDevice.taskExecutor.transformAsync(futureMountPoint, AdbDeviceFileEntry::entries)
    }

  override fun delete(): ListenableFuture<Unit> {
    val futureMountPoint = myDevice.resolveMountPoint(this)
    return myDevice.taskExecutor.transformAsync(futureMountPoint) { obj: AdbDeviceFileEntry? -> obj!!.delete() }
  }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> {
    val futureMountPoint = myDevice.resolveMountPoint(this)
    return myDevice.taskExecutor.transformAsync(futureMountPoint) { x: AdbDeviceFileEntry? ->
      assert(x != null)
      x!!.createNewFile(fileName)
    }
  }

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> {
    val futureMountPoint = myDevice.resolveMountPoint(this)
    return myDevice.taskExecutor.transformAsync(futureMountPoint) { x: AdbDeviceFileEntry? ->
      assert(x != null)
      x!!.createNewDirectory(directoryName)
    }
  }

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() {
      val futureMountPoint = myDevice.resolveMountPoint(this)
      return myDevice.taskExecutor.transformAsync(futureMountPoint, AdbDeviceFileEntry::isSymbolicLinkToDirectory)
    }

  override fun downloadFile(localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> {
    val futureMountPoint = myDevice.resolveMountPoint(this)
    return myDevice.taskExecutor.transformAsync(futureMountPoint) { x: AdbDeviceFileEntry? ->
      assert(x != null)
      x!!.downloadFile(localPath, progress)
    }
  }

  override fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress): ListenableFuture<Unit> {
    val futureMountPoint = myDevice.resolveMountPoint(this)
    return myDevice.taskExecutor.transformAsync(futureMountPoint) { x: AdbDeviceFileEntry? ->
      assert(x != null)
      x!!.uploadFile(localPath, fileName, progress)
    }
  }
}