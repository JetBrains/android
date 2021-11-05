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

import com.android.ddmlib.FileListingService
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.delayedError
import com.android.tools.idea.concurrency.delayedValue
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceState
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.Companion.createRoot
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Alarm
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlin.math.min

class MockDeviceFileSystem(val service: MockDeviceFileSystemService, override val name: String, taskExecutor: Executor) : DeviceFileSystem {

  override val deviceSerialNumber = name
  val root: MockDeviceFileEntry = createRoot(this)
  var downloadChunkSize: Long = 1024
  var uploadChunkSize: Long = 1024
  var downloadChunkIntervalMillis = OPERATION_TIMEOUT_MILLIS
  var uploadChunkIntervalMillis = OPERATION_TIMEOUT_MILLIS
  var downloadError: Throwable? = null
  var rootDirectoryError: Throwable? = null
  var uploadError: Throwable? = null
  private val myTaskExecutor = FutureCallbackExecutor(taskExecutor)

  override val deviceState: DeviceState
    get() = DeviceState.ONLINE

  override val rootDirectory: ListenableFuture<DeviceFileEntry>
    get() = when (val error = rootDirectoryError) {
      null -> delayedValue(this.root, OPERATION_TIMEOUT_MILLIS)
      else -> delayedError(error, OPERATION_TIMEOUT_MILLIS)
    }

  override fun getEntry(path: String): ListenableFuture<DeviceFileEntry> {
    val resultFuture = SettableFuture.create<DeviceFileEntry>()
    val currentDir = rootDirectory
    myTaskExecutor.addCallback(currentDir, object : FutureCallback<DeviceFileEntry> {
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
    future: SettableFuture<DeviceFileEntry>,
    currentEntry: DeviceFileEntry,
    segments: Array<String>,
    segmentIndex: Int
  ) {
    if (segmentIndex >= segments.size) {
      future.set(currentEntry)
      return
    }
    val entriesFuture = currentEntry.entries
    myTaskExecutor.addCallback(entriesFuture, object : FutureCallback<List<DeviceFileEntry>?> {
      override fun onSuccess(result: List<DeviceFileEntry>?) {
        checkNotNull(result)
        val entry = result.stream()
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

  fun downloadFile(entry: DeviceFileEntry, localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> =
    when (val error = downloadError) {
      null -> DownloadWorker(entry as MockDeviceFileEntry, localPath, progress).myFutureResult
      else -> delayedError(error, OPERATION_TIMEOUT_MILLIS)
    }

  fun uploadFile(localFilePath: Path,
    remoteDirectory: DeviceFileEntry,
    fileName: String,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> =
    when (val error = uploadError) {
      null -> UploadWorker(remoteDirectory as MockDeviceFileEntry, fileName, localFilePath, progress).myFutureResult
      else -> delayedError(error, OPERATION_TIMEOUT_MILLIS)
    }

  inner class DownloadWorker(
    private val myEntry: MockDeviceFileEntry,
    private val myPath: Path,
    private val myProgress: FileTransferProgress
  ) : Disposable {
    val myFutureResult: SettableFuture<Unit> = SettableFuture.create()
    private val myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private var myCurrentOffset: Long = 0
    private var myOutputStream: FileOutputStream? = null

    init {
      Disposer.register(ApplicationManager.getApplication(), this)
      addRequest()
    }

    private fun addRequest() {
      myAlarm.addRequest({ processNextChunk() }, downloadChunkIntervalMillis)
    }

    private fun processNextChunk() {
      assert(!myFutureResult.isDone)

      // Create file if needed
      try {
        writeBytes(0)
      } catch (e: IOException) {
        doneWithError(e)
        return
      }

      // Report progress
      val currentOffset = myCurrentOffset
      service.edtExecutor.execute { myProgress.progress(currentOffset, myEntry.size) }

      // Write bytes and enqueue next request if not done yet
      if (myCurrentOffset < myEntry.size) {
        addRequest()
        val chunkSize = min(downloadChunkSize, myEntry.size - myCurrentOffset).toInt()
        try {
          writeBytes(chunkSize)
          myCurrentOffset += chunkSize
        } catch (e: IOException) {
          doneWithError(e)
        }
        return
      }

      // Complete future if done
      done()
    }

    private fun done() {
      try {
        Disposer.dispose(this)
      } finally {
        myFutureResult.set(Unit)
      }
    }

    private fun doneWithError(t: Throwable) {
      try {
        Disposer.dispose(this)
      } finally {
        myFutureResult.setException(t)
      }
    }

    @Throws(IOException::class)
    private fun writeBytes(count: Int) {
      val outputStream = myOutputStream ?: FileOutputStream(myPath.toFile()).also { myOutputStream = it }
      if (count > 0) {
        val bytes = ByteArray(count)
        // Write ascii characters to that the file is easily auto-detected as a text file
        // in unit tests.
        for (i in 0 until count) {
          bytes[i] = (if (i % 80 == 0) '\n' else '0' + i % 10).toByte()
        }
        outputStream.write(bytes)
      }
    }

    override fun dispose() {
      myAlarm.cancelAllRequests()
      myOutputStream?.let {
        try {
          it.close()
          myOutputStream = null
        } catch (e: IOException) {
          throw RuntimeException(e)
        }
      }
    }
  }

  inner class UploadWorker(
    private val myEntry: MockDeviceFileEntry,
    private val myFileName: String,
    private val myPath: Path,
    private val myProgress: FileTransferProgress
  ) : Disposable {
    val myFutureResult: SettableFuture<Unit> = SettableFuture.create()
    private val myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private var myCurrentOffset: Long = 0
    private var myFileLength: Long = 0
    private var myCreatedEntry: MockDeviceFileEntry? = null

    init {
      Disposer.register(ApplicationManager.getApplication(), this)
      addRequest()
    }

    private fun addRequest() {
      myAlarm.addRequest({ processNextChunk() }, uploadChunkIntervalMillis)
    }

    private fun processNextChunk() {
      assert(!myFutureResult.isDone)

      // Add entry right away (simulate behavior of device upload, where an empty file is immediately created on upload)
      val createdEntry =
        myCreatedEntry ?: try {
          myFileLength = Files.size(myPath)
          myEntry.addFile(myFileName).also { myCreatedEntry = it }
        } catch (e: AdbShellCommandException) {
          doneWithError(e)
          return
        } catch (e: IOException) {
          doneWithError(e)
          return
        }

      // Report progress
      val currentOffset = myCurrentOffset
      service.edtExecutor.execute { myProgress.progress(currentOffset, myFileLength) }

      // Write bytes and enqueue next request if not done yet
      if (myCurrentOffset < myFileLength) {
        addRequest()
        val chunkSize = min(uploadChunkSize, myFileLength - myCurrentOffset)
        createdEntry.size += chunkSize
        myCurrentOffset += chunkSize
        return
      }

      // Complete future if done
      done()
    }

    private fun done() {
      try {
        Disposer.dispose(this)
      } finally {
        myFutureResult.set(Unit)
      }
    }

    private fun doneWithError(t: Throwable) {
      try {
        Disposer.dispose(this)
      } finally {
        myFutureResult.setException(t)
      }
    }

    override fun dispose() {
      myAlarm.cancelAllRequests()
    }
  }
}