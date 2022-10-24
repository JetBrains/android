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

import com.android.ddmlib.FileListingService
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.device.explorer.files.cancelAndThrow
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.android.tools.idea.device.explorer.files.fs.DeviceState
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileEntry.Companion.createRoot
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

class MockDeviceFileSystem(val service: MockDeviceFileSystemService, override val name: String) : DeviceFileSystem {

  override val deviceSerialNumber = name
  val root: MockDeviceFileEntry = createRoot(this)
  var downloadChunkSize: Long = 1024
  var uploadChunkSize: Long = 1024
  var downloadChunkIntervalMillis = OPERATION_TIMEOUT_MILLIS
  var uploadChunkIntervalMillis = OPERATION_TIMEOUT_MILLIS
  var downloadError: Throwable? = null
  var rootDirectoryError: Throwable? = null
  var uploadError: Throwable? = null

  override fun toString() = "MockDevice-$name"

  override val deviceStateFlow = MutableStateFlow(DeviceState.ONLINE)

  override val scope = CoroutineScope(EmptyCoroutineContext)

  override suspend fun rootDirectory(): DeviceFileEntry {
    delay(OPERATION_TIMEOUT_MILLIS)
    rootDirectoryError?.let { throw it }
    return this.root
  }

  override suspend fun getEntry(path: String): DeviceFileEntry {
    val root = rootDirectory()
    if (StringUtil.isEmpty(path) || StringUtil.equals(path, FileListingService.FILE_SEPARATOR)) {
      return root
    }
    val pathSegments = path.substring(1).split(FileListingService.FILE_SEPARATOR.toRegex()).toList()
    return resolvePathSegments(root, pathSegments)
  }

  private suspend fun resolvePathSegments(
    rootEntry: DeviceFileEntry,
    segments: List<String>
  ): DeviceFileEntry {
    var currentEntry = rootEntry
    for (segment in segments) {
      currentEntry = currentEntry.entries().find { it.name == segment } ?: throw IllegalArgumentException("Path not found")
    }
    return currentEntry
  }

  suspend fun downloadFile(entry: DeviceFileEntry, localPath: Path, progress: FileTransferProgress) {
    delay(OPERATION_TIMEOUT_MILLIS)
    downloadError?.let { throw it }

    withContext(diskIoThread) {
      delay(downloadChunkIntervalMillis)

      // Create file if needed
      FileOutputStream(localPath.toFile()).use { outputStream ->
        var currentOffset: Long = 0
        progress.report(currentOffset, entry.size)

        while (currentOffset < entry.size) {
          if (progress.isCancelled) {
            cancelAndThrow()
          }

          // Write bytes
          val chunkSize = min(downloadChunkSize, entry.size - currentOffset).toInt()
          writeBytes(outputStream, chunkSize)
          currentOffset += chunkSize

          delay(downloadChunkIntervalMillis)
          progress.report(currentOffset, entry.size)
        }
      }
    }
  }

  private fun FileTransferProgress.report(currentBytes: Long, totalBytes: Long) {
    service.edtExecutor.execute { progress(currentBytes, totalBytes) }
  }

  private fun writeBytes(outputStream: OutputStream, count: Int) {
    if (count > 0) {
      val bytes = ByteArray(count)
      // Write ascii characters to that the file is easily auto-detected as a text file
      // in unit tests.
      for (i in 0 until count) {
        bytes[i] = (if (i % 80 == 0) '\n' else '0' + i % 10).code.toByte()
      }
      outputStream.write(bytes)
    }
  }

  suspend fun uploadFile(localFilePath: Path,
    remoteDirectory: DeviceFileEntry,
    fileName: String,
    progress: FileTransferProgress
  ) {
    delay(OPERATION_TIMEOUT_MILLIS)
    uploadError?.let { throw it }

    if (remoteDirectory !is MockDeviceFileEntry) {
      throw AssertionError("Expected MockDeviceFileEntry")
    }

    withContext(diskIoThread) {
      delay(uploadChunkIntervalMillis)

      val fileLength = Files.size(localFilePath)

      // Add entry right away (simulate behavior of device upload, where an empty file is immediately created on upload)
      val createdEntry = remoteDirectory.addFile(fileName)

      var currentOffset: Long = 0
      progress.report(currentOffset, fileLength)

      while (currentOffset < fileLength) {
        if (progress.isCancelled) {
          cancelAndThrow()
        }

        val chunkSize = min(uploadChunkSize, fileLength - currentOffset)
        createdEntry.size += chunkSize
        currentOffset += chunkSize

        delay(uploadChunkIntervalMillis)
        progress.report(currentOffset, fileLength)
      }
    }
  }
}