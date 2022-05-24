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
package com.android.tools.idea.explorer.mocks

import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.concurrency.delayedError
import com.android.tools.idea.concurrency.delayedOperation
import com.android.tools.idea.concurrency.delayedValue
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.google.common.util.concurrent.ListenableFuture
import java.nio.file.Path

class MockDeviceFileEntry(
  override val fileSystem: MockDeviceFileSystem,
  override val parent: MockDeviceFileEntry?,
  override val name: String,
  override val isDirectory: Boolean,
  override val isSymbolicLink: Boolean,
  override val symbolicLinkTarget: String?,
  private var myIsSymbolicLinkToDirectory: Boolean = false,
  var getEntriesTimeoutMillis: Int = OPERATION_TIMEOUT_MILLIS
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

  override val entries: ListenableFuture<List<DeviceFileEntry>>
    get() =
      when (val error = getEntriesError) {
        null -> delayedValue(myEntries.toList(), getEntriesTimeoutMillis)
        else -> delayedError(error, getEntriesTimeoutMillis)
      }

  override fun delete(): ListenableFuture<Unit> =
    when (val error = deleteError) {
      null -> delayedOperation({ parent?.removeEntry(this); Unit }, OPERATION_TIMEOUT_MILLIS)
      else -> delayedError(error, OPERATION_TIMEOUT_MILLIS)
    }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> {
    return delayedOperation({ addFile(fileName) }, OPERATION_TIMEOUT_MILLIS)
  }

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> {
    return delayedOperation({ addDirectory(directoryName) }, OPERATION_TIMEOUT_MILLIS)
  }

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() = delayedValue(myIsSymbolicLinkToDirectory, OPERATION_TIMEOUT_MILLIS)

  override fun downloadFile(localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> {
    return fileSystem.downloadFile(this, localPath, progress)
  }

  override fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress): ListenableFuture<Unit> {
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