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
package com.android.tools.idea.device.explorer.files.mocks

import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.device.explorer.files.adbimpl.AdbPathUtil
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import kotlinx.coroutines.delay
import java.nio.file.Path

class MockDeviceFileEntry(
  override val fileSystem: MockDeviceFileSystem,
  override val parent: MockDeviceFileEntry?,
  override val name: String,
  override val isDirectory: Boolean,
  override val isSymbolicLink: Boolean,
  override val symbolicLinkTarget: String?,
  private var myIsSymbolicLinkToDirectory: Boolean = false,
  var getEntriesTimeoutMillis: Long = OPERATION_TIMEOUT_MILLIS
) : DeviceFileEntry {
  init {
    parent?.myEntries?.add(this)
  }

  private val myEntries: MutableList<MockDeviceFileEntry> = ArrayList()
  override var size: Long = 0
  var getEntriesError: Throwable? = null
  var deleteError: Throwable? = null
  override fun toString() = name

  @Throws(AdbShellCommandException::class)
  fun addFile(name: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    return MockDeviceFileEntry(fileSystem, this, name, isDirectory = false, isSymbolicLink = false, symbolicLinkTarget = null)
  }

  @Throws(AdbShellCommandException::class)
  fun addDirLink(name: String, linkTarget: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    val entry = MockDeviceFileEntry(fileSystem, this, name,
                                    isDirectory = false,
                                    isSymbolicLink = true,
                                    symbolicLinkTarget = linkTarget,
                                    myIsSymbolicLinkToDirectory = true)
    return entry
  }

  @Throws(AdbShellCommandException::class)
  fun addFileLink(name: String, linkTarget: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    return MockDeviceFileEntry(fileSystem, this, name, isDirectory = false, isSymbolicLink = true, symbolicLinkTarget = linkTarget)
  }

  @Throws(AdbShellCommandException::class)
  fun addDirectory(name: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    return MockDeviceFileEntry(fileSystem, this, name, true, false, null)
  }

  private fun removeEntry(childEntry: MockDeviceFileEntry) {
    myEntries.remove(childEntry)
  }

  @Throws(AdbShellCommandException::class)
  private fun throwIfEntryExists(name: String) {
    if (myEntries.any { it.name == name }) {
      throw AdbShellCommandException("File already exists")
    }
  }

  val mockEntries: List<MockDeviceFileEntry>
    get() = myEntries

  override val fullPath: String
    get() = when (parent) {
      null -> name
      else -> AdbPathUtil.resolve(parent.fullPath, name)
    }

  override suspend fun entries(): List<DeviceFileEntry> {
    delay(getEntriesTimeoutMillis)
    getEntriesError?.let { throw it }
    return myEntries.toList()
  }

  override suspend fun delete() {
    delay(OPERATION_TIMEOUT_MILLIS)
    deleteError?.let { throw it }
    parent?.removeEntry(this)
  }

  override suspend fun createNewFile(fileName: String) {
    delay(OPERATION_TIMEOUT_MILLIS)
    addFile(fileName)
  }

  override suspend fun createNewDirectory(directoryName: String) {
    delay(OPERATION_TIMEOUT_MILLIS)
    addDirectory(directoryName)
  }

  override suspend fun isSymbolicLinkToDirectory(): Boolean {
    delay(OPERATION_TIMEOUT_MILLIS)
    return myIsSymbolicLinkToDirectory
  }

  override suspend fun downloadFile(localPath: Path, progress: FileTransferProgress) {
    return fileSystem.downloadFile(this, localPath, progress)
  }

  override suspend fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress) {
    return fileSystem.uploadFile(localPath, this, fileName, progress)
  }

  override val permissions =
    object : DeviceFileEntry.Permissions {
      override val text = "rwxrwxrwx"
    }

  override val lastModifiedDate =
    object : DeviceFileEntry.DateTime {
      override val text = ""
    }

  override val isFile: Boolean
    get() = !isDirectory

  companion object {
    @JvmStatic
    fun createRoot(fileSystem: MockDeviceFileSystem): MockDeviceFileEntry {
      return MockDeviceFileEntry(fileSystem, null, "", isDirectory = true, isSymbolicLink = false, symbolicLinkTarget = null)
    }
  }
}