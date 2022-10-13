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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * A custom [AdbDeviceFileEntry] implementation for the the "/data" directory of a device.
 *
 * The purpose is to allow file operations on files under "/data/data/packageName" using the
 * "run-as" command shell prefix.
 */
class AdbDeviceDataDirectoryEntry(entry: AdbDeviceFileEntry)
  : AdbDeviceForwardingFileEntry(AdbDeviceDirectFileEntry(entry.fileSystem, entry.myEntry, entry.parent, null)) {

  override suspend fun entries(): List<DeviceFileEntry> =
    withContext(fileSystem.dispatcher) {
      listOf(AdbDeviceDataAppDirectoryEntry(this@AdbDeviceDataDirectoryEntry, createDirectoryEntry(myEntry, "app")),
             AdbDeviceDataDataDirectoryEntry(this@AdbDeviceDataDirectoryEntry, createDirectoryEntry(myEntry, "data")),
             AdbDeviceDataLocalDirectoryEntry(this@AdbDeviceDataDirectoryEntry, createDirectoryEntry(myEntry, "local")))
    }

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
  ) : AdbDeviceForwardingFileEntry(AdbDeviceDirectFileEntry(parent.fileSystem, entry, parent, null)) {

    override suspend fun entries(): List<DeviceFileEntry> =
        // Create an entry for each package returned by "pm list packages"
        fileSystem.adbFileOperations.listPackages().map { packageName: String ->
          AdbDevicePackageDirectoryEntry(this, createDirectoryEntry(myEntry, packageName), packageName)
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
  ) : AdbDeviceForwardingFileEntry(AdbDeviceDirectFileEntry(parent.fileSystem, entry, parent, null)) {

    override suspend fun entries(): List<DeviceFileEntry> =
        // Create an entry for each package returned by "pm list packages"
      fileSystem.adbFileOperations.listPackageInfo().mapNotNull { info: AdbFileOperations.PackageInfo ->
        val segments = AdbPathUtil.getSegments(info.path)
        if (segments.size >= 3 && segments[0] == "data" && segments[1] == "app") {
          if (segments.size == 3) {
            // Some package paths are files directly inside the "/data/app" directory
            AdbDeviceDirectFileEntry(fileSystem, createFileEntry(myEntry, segments[2]), this, info.packageName)
          } else {
            // Most package paths are directories inside the "/data/app" directory
            AdbDevicePackageDirectoryEntry(this, createDirectoryEntry(myEntry, segments[2]), info.packageName)
          }
        }
        else null
      }


  }

  private class AdbDeviceDataLocalDirectoryEntry(
    parent: AdbDeviceFileEntry,
    entry: AdbFileListingEntry
  ) : AdbDeviceForwardingFileEntry(AdbDeviceDirectFileEntry(parent.fileSystem, entry, parent, null)) {

    override suspend fun entries(): List<DeviceFileEntry> =
      withContext(fileSystem.dispatcher) {
        listOf(AdbDeviceDirectFileEntry(
          fileSystem, createDirectoryEntry(myEntry, "tmp"), this@AdbDeviceDataLocalDirectoryEntry, null))
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
  ) : AdbDeviceForwardingFileEntry(AdbDeviceDirectFileEntry(parent.fileSystem, entry, parent, myPackageName)) {

    override suspend fun entries(): List<DeviceFileEntry> =
      // Create "run-as" entries for child entries
      fileSystem.adbFileListing.getChildrenRunAs(myEntry, myPackageName).map {
        AdbDevicePackageDirectoryEntry(this, it, myPackageName)
      }

    override suspend fun downloadFile(localPath: Path, progress: FileTransferProgress) {
      // Note: We should reach this code only if the device is not root, in which case
      // trying a "pullFile" would fail because of permission error (reading from the /data/data/
      // directory), so we copy the file to a temp. location, then pull from that temp location.
      fileSystem.adbFileTransfer.downloadFileViaTempLocation(fullPath, size, localPath, progress, myPackageName)
    }

    override suspend fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress) {
      // Note: We should reach this code only if the device is not root, in which case
      // trying a "pushFile" would fail because of permission error (writing to the /data/data/
      // directory), so we use the push to temporary location, then copy to final location.
      //
      // We do this directly instead of doing it as a fallback to attempting a regular push
      // because of https://code.google.com/p/android/issues/detail?id=241157.
      fileSystem.adbFileTransfer.uploadFileViaTempLocation(
        localPath,
        AdbPathUtil.resolve(fullPath, fileName),
        progress,
        myPackageName
      )
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
}