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

import com.android.tools.idea.concurrency.delayedError
import com.android.tools.idea.concurrency.delayedValue
import com.android.tools.idea.concurrency.delayedOperation
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import kotlin.Throws
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.google.common.util.concurrent.ListenableFuture
import java.nio.file.Path
import java.util.ArrayList
import java.util.stream.Collectors

class MockDeviceFileEntry(
  private val myFileSystem: MockDeviceFileSystem,
  private val myParent: MockDeviceFileEntry?,
  name: String,
  isDirectory: Boolean,
  isLink: Boolean,
  linkTarget: String?
) : DeviceFileEntry {
  private val myEntries: MutableList<MockDeviceFileEntry> = ArrayList()
  override val name: String
  override val isDirectory: Boolean
  override val isSymbolicLink: Boolean
  override val symbolicLinkTarget: String?
  override var size: Long = 0
  private var myIsSymbolicLinkToDirectory = false
  private var myGetEntriesError: Throwable? = null
  private var myDeleteError: Throwable? = null
  private var myGetEntriesTimeoutMillis = OPERATION_TIMEOUT_MILLIS
  override fun toString(): String {
    return name
  }

  @Throws(AdbShellCommandException::class)
  fun addFile(name: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    return MockDeviceFileEntry(myFileSystem, this, name, false, false, null)
  }

  @Throws(AdbShellCommandException::class)
  fun addDirLink(name: String, linkTarget: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    val entry = MockDeviceFileEntry(myFileSystem, this, name, false, true, linkTarget)
    entry.setSymbolicLinkToDirectory(true)
    return entry
  }

  @Throws(AdbShellCommandException::class)
  fun addFileLink(name: String, linkTarget: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    return MockDeviceFileEntry(myFileSystem, this, name, false, true, linkTarget)
  }

  @Throws(AdbShellCommandException::class)
  fun addDirectory(name: String): MockDeviceFileEntry {
    assert(isDirectory)
    throwIfEntryExists(name)
    return MockDeviceFileEntry(myFileSystem, this, name, true, false, null)
  }

  private fun removeEntry(childEntry: MockDeviceFileEntry) {
    myEntries.remove(childEntry)
  }

  @Throws(AdbShellCommandException::class)
  private fun throwIfEntryExists(name: String) {
    if (myEntries.stream().anyMatch { x: MockDeviceFileEntry -> x.name == name }) {
      throw AdbShellCommandException("File already exists")
    }
  }

  val mockEntries: List<MockDeviceFileEntry>
    get() = myEntries
  override val fileSystem: DeviceFileSystem
    get() = myFileSystem
  override val parent: DeviceFileEntry?
    get() = myParent
  override val fullPath: String
    get() = if (myParent == null) {
      name
    } else AdbPathUtil.resolve(myParent.fullPath, name)
  override val entries: ListenableFuture<List<DeviceFileEntry>>
    get() = if (myGetEntriesError != null) {
      delayedError(myGetEntriesError!!, myGetEntriesTimeoutMillis)
    } else delayedValue(
      myEntries.stream().collect(
        Collectors.toList()
      ), myGetEntriesTimeoutMillis
    )

  override fun delete(): ListenableFuture<Unit> {
    return if (myDeleteError != null) {
      delayedError(myDeleteError!!, OPERATION_TIMEOUT_MILLIS)
    } else delayedOperation({
                              myParent!!.removeEntry(this)
                              Unit
                            }, OPERATION_TIMEOUT_MILLIS)
  }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> {
    return delayedOperation({
                              addFile(fileName)
                              Unit
                            }, OPERATION_TIMEOUT_MILLIS)
  }

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> {
    return delayedOperation({
                              addDirectory(directoryName)
                              Unit
                            }, OPERATION_TIMEOUT_MILLIS)
  }

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() = delayedValue(myIsSymbolicLinkToDirectory, OPERATION_TIMEOUT_MILLIS)

  override fun downloadFile(localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> {
    return myFileSystem.downloadFile(this, localPath, progress)
  }

  override fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress): ListenableFuture<Unit> {
    return myFileSystem.uploadFile(localPath, this, fileName, progress)
  }

  override val permissions: DeviceFileEntry.Permissions
    get() = DeviceFileEntry.Permissions { "rwxrwxrwx" }
  override val lastModifiedDate: DeviceFileEntry.DateTime
    get() = DeviceFileEntry.DateTime { "" }
  override val isFile: Boolean
    get() = !isDirectory

  fun setGetEntriesError(t: Throwable?) {
    myGetEntriesError = t
  }

  fun setGetEntriesTimeoutMillis(timeoutMillis: Int) {
    myGetEntriesTimeoutMillis = timeoutMillis
  }

  fun setSymbolicLinkToDirectory(symbolicLinkToDirectory: Boolean) {
    myIsSymbolicLinkToDirectory = symbolicLinkToDirectory
  }

  fun setDeleteError(t: Throwable?) {
    myDeleteError = t
  }

  companion object {
    @JvmStatic
    fun createRoot(fileSystem: MockDeviceFileSystem): MockDeviceFileEntry {
      return MockDeviceFileEntry(fileSystem, null, "", true, false, null)
    }
  }

  init {
    myParent?.myEntries?.add(this)
    this.name = name
    this.isDirectory = isDirectory
    isSymbolicLink = isLink
    symbolicLinkTarget = linkTarget
  }
}