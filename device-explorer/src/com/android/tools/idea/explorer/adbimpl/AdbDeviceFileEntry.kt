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
import com.intellij.openapi.util.text.StringUtil
import java.io.IOException

/**
 * Abstract base class for all implementations of [DeviceFileEntry] that rely on
 * an underlying [AdbFileListingEntry].
 */
abstract class AdbDeviceFileEntry(
  val myDevice: AdbDeviceFileSystem,
  val myEntry: AdbFileListingEntry,
  override val parent: AdbDeviceFileEntry?
) : DeviceFileEntry {
  override fun toString(): String {
    return myEntry.toString()
  }

  override val fileSystem: DeviceFileSystem
    get() = myDevice
  override val name: String
    get() = myEntry.name
  override val fullPath: String
    get() = myEntry.fullPath
  override val permissions: DeviceFileEntry.Permissions
    get() = AdbPermissions(myEntry.permissions)
  override val lastModifiedDate: DeviceFileEntry.DateTime
    get() = AdbDateTime(myEntry.date!!, myEntry.time)
  override val size: Long
    get() = myEntry.size
  override val isDirectory: Boolean
    get() = myEntry.isDirectory
  override val isFile: Boolean
    get() = myEntry.isFile
  override val isSymbolicLink: Boolean
    get() = myEntry.isSymbolicLink
  override val symbolicLinkTarget: String?
    get() {
      if (!isSymbolicLink) {
        return null
      }
      val info = myEntry.info
      return if (StringUtil.isEmpty(info) || !info!!.startsWith(SYMBOLIC_LINK_INFO_PREFIX)) {
        null
      } else info.substring(SYMBOLIC_LINK_INFO_PREFIX.length)
    }

  class AdbPermissions(private val myValue: String?) : DeviceFileEntry.Permissions {
    override val text: String
      get() = StringUtil.notNullize(myValue)
  }

  class AdbDateTime(private val myDate: String, private val myTime: String?) : DeviceFileEntry.DateTime {
    override val text: String
      get() {
        if (StringUtil.isEmpty(myDate)) {
          return ""
        }
        return if (StringUtil.isEmpty(myTime)) {
          myDate
        } else "$myDate $myTime"
      }
  }

  companion object {
    const val SYMBOLIC_LINK_INFO_PREFIX = "-> "
  }
}