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
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import java.io.IOException
import java.nio.file.Path
import java.util.ArrayList
import java.util.Objects
import java.util.concurrent.Callable
import java.util.stream.Collectors

/**
 * A custom [AdbDeviceFileEntry] implementation for the the "/data" directory of a device.
 *
 *
 * The purpose is to allow file operations on files under "/data/data/packageName" using the
 * "run-as" command shell prefix.
 */
class AdbDeviceDataDirectoryEntry(entry: AdbDeviceFileEntry) : AdbDeviceForwardingFileEntry(entry.myDevice, entry.myEntry, entry.myParent) {
  private val myDirectEntry: AdbDeviceDirectFileEntry
  override val forwardedFileEntry: AdbDeviceFileEntry
    get() = myDirectEntry
  override val entries: ListenableFuture<List<DeviceFileEntry?>?>
    get() = myDevice.taskExecutor.executeAsync(Callable<List<DeviceFileEntry>> {
      val entries: MutableList<DeviceFileEntry> = ArrayList()
      entries.add(AdbDeviceDataAppDirectoryEntry(this, createDirectoryEntry(myEntry, "app")))
      entries.add(AdbDeviceDataDataDirectoryEntry(this, createDirectoryEntry(myEntry, "data")))
      entries.add(AdbDeviceDataLocalDirectoryEntry(this, createDirectoryEntry(myEntry, "local")))
      entries
    })

  /**
   * A custom [AdbDeviceFileEntry] implementation for the the "/data/data" directory of a device.
   *
   *
   * The purpose is to allow file operations on files under "/data/data/packageName" using the
   * "run-as" command shell prefix.
   */
  private class AdbDeviceDataDataDirectoryEntry(
    parent: AdbDeviceFileEntry,
    entry: AdbFileListingEntry
  ) : AdbDeviceForwardingFileEntry(parent.myDevice, entry, parent) {
    private val myDirectEntry: AdbDeviceDirectFileEntry
    override fun getForwardedFileEntry(): AdbDeviceFileEntry {
      return myDirectEntry
    }

    override fun getEntries(): ListenableFuture<List<DeviceFileEntry>> {
      // Create an entry for each package returned by "pm list packages"
      val futurePackages = myDevice.adbFileOperations.listPackages()
      return myDevice.taskExecutor.transform(futurePackages) { packages: List<String>? ->
        assert(packages != null)
        packages!!.stream()
          .map { packageName: String -> AdbDevicePackageDirectoryEntry(this, createDirectoryEntry(myEntry, packageName), packageName) }
          .collect(Collectors.toList())
      }
    }

    init {
      myDirectEntry = AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, null)
    }
  }

  /**
   * A custom [AdbDeviceFileEntry] implementation for the the "/data/app" directory of a device.
   *
   *
   * The purpose is to allow file operations on files under "/data/app/packageName" using the
   * "run-as" command shell prefix.
   */
  private class AdbDeviceDataAppDirectoryEntry(
    parent: AdbDeviceFileEntry,
    entry: AdbFileListingEntry
  ) : AdbDeviceForwardingFileEntry(parent.myDevice, entry, parent) {
    private val myDirectEntry: AdbDeviceDirectFileEntry
    override fun getForwardedFileEntry(): AdbDeviceFileEntry {
      return myDirectEntry
    }

    override fun getEntries(): ListenableFuture<List<DeviceFileEntry>> {
      // Create an entry for each package returned by "pm list packages"
      val futurePackages = myDevice.adbFileOperations.listPackageInfo()
      return myDevice.taskExecutor.transform(futurePackages) { packages: List<AdbFileOperations.PackageInfo>? ->
        assert(packages != null)
        packages!!.stream()
          .map { info: AdbFileOperations.PackageInfo ->
            val segments = AdbPathUtil.getSegments(info.path)
            if (segments.size <= 2) {
              return@map null
            }
            if ("data" != segments[0]) {
              return@map null
            }
            if ("app" != segments[1]) {
              return@map null
            }
            if (segments.size == 3) {
              // Some package paths are files directly inside the "/data/app" directory
              val entry = createFileEntry(myEntry, segments[2])
              return@map AdbDeviceDirectFileEntry(myDevice, entry, this, info.packageName)
            } else {
              // Most package paths are directories inside the "/data/app" directory
              val entry = createDirectoryEntry(myEntry, segments[2])
              return@map AdbDevicePackageDirectoryEntry(this, entry, info.packageName)
            }
          }
          .filter { obj: AdbDeviceFileEntry? -> Objects.nonNull(obj) }
          .collect(Collectors.toList())
      }
    }

    init {
      myDirectEntry = AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, null)
    }
  }

  private class AdbDeviceDataLocalDirectoryEntry(
    parent: AdbDeviceFileEntry,
    entry: AdbFileListingEntry
  ) : AdbDeviceForwardingFileEntry(parent.myDevice, entry, parent) {
    private val myDirectEntry: AdbDeviceDirectFileEntry
    override fun getForwardedFileEntry(): AdbDeviceFileEntry {
      return myDirectEntry
    }

    override fun getEntries(): ListenableFuture<List<DeviceFileEntry>> {
      return myDevice.taskExecutor.executeAsync {
        val entries: MutableList<DeviceFileEntry> = ArrayList()
        entries.add(AdbDeviceDirectFileEntry(myDevice, createDirectoryEntry(myEntry, "tmp"), this, null))
        entries
      }
    }

    init {
      myDirectEntry = AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, null)
    }
  }

  /**
   * A custom [AdbDeviceFileEntry] implementation for a "/data/data/package-name" directory of a device.
   *
   *
   * Use the "run-as" command shell prefix for all file operations.
   */
  private class AdbDevicePackageDirectoryEntry(
    parent: AdbDeviceFileEntry,
    entry: AdbFileListingEntry,
    private val myPackageName: String
  ) : AdbDeviceForwardingFileEntry(parent.myDevice, entry, parent) {
    private val myDirectEntry: AdbDeviceDirectFileEntry
    override fun getForwardedFileEntry(): AdbDeviceFileEntry {
      return myDirectEntry
    }

    override fun getEntries(): ListenableFuture<List<DeviceFileEntry>> {
      // Create "run-as" entries for child entries
      val futureChildren = myDevice.adbFileListing.getChildrenRunAs(myEntry, myPackageName)
      return myDevice.taskExecutor.transform(futureChildren) { entries: List<AdbFileListingEntry>? ->
        assert(entries != null)
        ContainerUtil.map(
          entries,
          Function<AdbFileListingEntry, DeviceFileEntry> { x: AdbFileListingEntry ->
            AdbDevicePackageDirectoryEntry(
              this,
              x,
              myPackageName
            )
          })
      }
    }

    override fun downloadFile(localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> {
      // Note: We should reach this code only if the device is not root, in which case
      // trying a "pullFile" would fail because of permission error (reading from the /data/data/
      // directory), so we copy the file to a temp. location, then pull from that temp location.
      return myDevice.adbFileTransfer.downloadFileViaTempLocation(getFullPath(), getSize(), localPath, progress, myPackageName)
    }

    override fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress): ListenableFuture<Unit> {
      // Note: We should reach this code only if the device is not root, in which case
      // trying a "pushFile" would fail because of permission error (writing to the /data/data/
      // directory), so we use the push to temporary location, then copy to final location.
      //
      // We do this directly instead of doing it as a fallback to attempting a regular push
      // because of https://code.google.com/p/android/issues/detail?id=241157.
      return myDevice.adbFileTransfer.uploadFileViaTempLocation(
        localPath,
        AdbPathUtil.resolve(getFullPath(), fileName),
        progress,
        myPackageName
      )
    }

    init {
      myDirectEntry = AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, myPackageName)
    }
  }

  companion object {
    private fun createDirectoryEntry(parent: AdbFileListingEntry, name: String): AdbFileListingEntry {
      return AdbFileListingEntryBuilder(parent)
        .setPath(AdbPathUtil.resolve(parent.fullPath, name))
        .build()
    }

    private fun createFileEntry(parent: AdbFileListingEntry, name: String): AdbFileListingEntry {
      return AdbFileListingEntryBuilder(parent)
        .setPath(AdbPathUtil.resolve(parent.fullPath, name))
        .setKind(AdbFileListingEntry.EntryKind.FILE)
        .setSize(-1)
        .build()
    }
  }

  init {
    myDirectEntry = AdbDeviceDirectFileEntry(entry.myDevice, entry.myEntry, entry.myParent, null)
  }
}