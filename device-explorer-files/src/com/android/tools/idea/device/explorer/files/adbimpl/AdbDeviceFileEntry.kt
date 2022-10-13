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
import com.intellij.openapi.util.text.StringUtil

/**
 * Abstract base class for all implementations of [DeviceFileEntry] that rely on
 * an underlying [AdbFileListingEntry].
 */
abstract class AdbDeviceFileEntry(
  override val fileSystem: AdbDeviceFileSystem,
  internal val myEntry: AdbFileListingEntry,
  override val parent: AdbDeviceFileEntry?
) : DeviceFileEntry {

  override fun toString() = myEntry.toString()

  override val name: String
    get() = myEntry.name
  override val fullPath: String
    get() = myEntry.fullPath
  override val permissions: DeviceFileEntry.Permissions
    get() = AdbPermissions(myEntry.permissions)
  override val lastModifiedDate: DeviceFileEntry.DateTime
    get() = AdbDateTime(myEntry.date, myEntry.time)
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
      if (isSymbolicLink) {
        val info = myEntry.info ?: return null
        if (info.startsWith(SYMBOLIC_LINK_INFO_PREFIX)) {
          return info.substring(SYMBOLIC_LINK_INFO_PREFIX.length)
        }
      }
      return null
    }

  class AdbPermissions(private val myValue: String?) : DeviceFileEntry.Permissions {
    override val text: String
      get() = StringUtil.notNullize(myValue)
  }

  class AdbDateTime(private val myDate: String?, private val myTime: String?) : DeviceFileEntry.DateTime {
    override val text: String
      get() =
        if (StringUtil.isEmpty(myDate)) {
          ""
        } else if (StringUtil.isEmpty(myTime)) {
          myDate!!
        } else "$myDate $myTime"
  }

  companion object {
    const val SYMBOLIC_LINK_INFO_PREFIX = "-> "
  }
}