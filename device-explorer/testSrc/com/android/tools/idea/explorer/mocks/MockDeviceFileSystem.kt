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

import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.Companion.createRoot
import com.android.tools.idea.concurrency.delayedError
import com.android.tools.idea.concurrency.delayedValue
import com.android.tools.idea.explorer.fs.DeviceFileEntry.entries
import com.android.tools.idea.explorer.fs.DeviceFileEntry.name
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService.edtExecutor
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.size
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.addFile
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.ddmlib.FileListingService
import java.lang.IllegalArgumentException
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.DownloadWorker
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.UploadWorker
import com.intellij.util.Alarm
import java.io.FileOutputStream
import java.lang.Runnable
import java.io.IOException
import kotlin.Throws
import java.lang.RuntimeException
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.explorer.fs.DeviceState
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor

class MockDeviceFileSystem(val service: MockDeviceFileSystemService, override val name: String, taskExecutor: Executor) : DeviceFileSystem {
  override val deviceSerialNumber: String
  val root: MockDeviceFileEntry
  private var myDownloadChunkSize: Long = 1024
  private var myUploadChunkSize: Long = 1024
  private var myDownloadFileChunkIntervalMillis = OPERATION_TIMEOUT_MILLIS
  private var myUploadFileChunkIntervalMillis = OPERATION_TIMEOUT_MILLIS
  private var myDownloadError: Throwable? = null
  private var myRootDirectoryError: Throwable? = null
  private var myUploadError: Throwable? = null
  private val myTaskExectuor: FutureCallbackExecutor
  override val deviceState: DeviceState
    get() = DeviceState.ONLINE
  override val rootDirectory: ListenableFuture<DeviceFileEntry>
    get() = if (myRootDirectoryError != null) {
      delayedError(myRootDirectoryError!!, OPERATION_TIMEOUT_MILLIS)
    } else delayedValue(this.root, OPERATION_TIMEOUT_MILLIS)

  override fun getEntry(path: String): ListenableFuture<DeviceFileEntry?> {
    val resultFuture = SettableFuture.create<DeviceFileEntry?>()
    val currentDir = rootDirectory
    myTaskExectuor.addCallback(currentDir, object : FutureCallback<DeviceFileEntry?> {
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
    future: SettableFuture<DeviceFileEntry?>,
    currentEntry: DeviceFileEntry,
    segments: Array<String>,
    segmentIndex: Int
  ) {
    if (segmentIndex >= segments.size) {
      future.set(currentEntry)
      return
    }
    val entriesFuture = currentEntry.entries
    myTaskExectuor.addCallback(entriesFuture, object : FutureCallback<List<DeviceFileEntry>?> {
      override fun onSuccess(result: List<DeviceFileEntry>?) {
        assert(result != null)
        val entry = result
          .stream()
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

  fun downloadFile(
    entry: DeviceFileEntry,
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    return if (myDownloadError != null) {
      delayedError(myDownloadError!!, OPERATION_TIMEOUT_MILLIS)
    } else DownloadWorker(entry as MockDeviceFileEntry, localPath, progress).myFutureResult
  }

  fun uploadFile(
    localFilePath: Path,
    remoteDirectory: DeviceFileEntry,
    fileName: String,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    return if (myUploadError != null) {
      delayedError(myUploadError!!, OPERATION_TIMEOUT_MILLIS)
    } else UploadWorker(remoteDirectory as MockDeviceFileEntry, fileName, localFilePath, progress).myFutureResult
  }

  fun setDownloadFileChunkSize(size: Long) {
    myDownloadChunkSize = size
  }

  fun setDownloadFileChunkIntervalMillis(millis: Int) {
    myDownloadFileChunkIntervalMillis = millis
  }

  fun setUploadFileChunkSize(size: Long) {
    myUploadChunkSize = size
  }

  fun setUploadFileChunkIntervalMillis(millis: Int) {
    myUploadFileChunkIntervalMillis = millis
  }

  fun setDownloadError(t: Throwable?) {
    myDownloadError = t
  }

  fun setRootDirectoryError(t: Throwable?) {
    myRootDirectoryError = t
  }

  fun setUploadError(t: Throwable?) {
    myUploadError = t
  }

  inner class DownloadWorker(
    private val myEntry: MockDeviceFileEntry,
    private val myPath: Path,
    private val myProgress: FileTransferProgress
  ) : Disposable {
    val myFutureResult: SettableFuture<Unit>
    private val myAlarm: Alarm
    private var myCurrentOffset: Long = 0
    private var myOutputStream: FileOutputStream? = null
    private fun addRequest() {
      myAlarm.addRequest({ processNextChunk() }, myDownloadFileChunkIntervalMillis)
    }

    fun processNextChunk() {
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
        val chunkSize = Math.min(myDownloadChunkSize, myEntry.size - myCurrentOffset)
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
    private fun writeBytes(count: Long) {
      if (myOutputStream == null) {
        myOutputStream = FileOutputStream(myPath.toFile())
      }
      if (count > 0) {
        val bytes = ByteArray(count.toInt())
        // Write ascii characters to that the file is easily auto-detected as a text file
        // in unit tests.
        for (i in 0 until count) {
          bytes.get(i) = (if (i % 80 == 0) '\n' else '0'.toInt() + i % 10) as Byte
        }
        myOutputStream!!.write(bytes)
      }
    }

    override fun dispose() {
      myAlarm.cancelAllRequests()
      if (myOutputStream != null) {
        myOutputStream = try {
          myOutputStream!!.close()
          null
        } catch (e: IOException) {
          throw RuntimeException(e)
        }
      }
    }

    init {
      myFutureResult = SettableFuture.create()
      myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
      Disposer.register(ApplicationManager.getApplication(), this)
      addRequest()
    }
  }

  inner class UploadWorker(
    private val myEntry: MockDeviceFileEntry,
    private val myFileName: String,
    private val myPath: Path,
    private val myProgress: FileTransferProgress
  ) : Disposable {
    val myFutureResult: SettableFuture<Unit>
    private val myAlarm: Alarm
    private var myCurrentOffset: Long = 0
    private var myFileLength: Long = 0
    private var myCreatedEntry: MockDeviceFileEntry? = null
    private fun addRequest() {
      myAlarm.addRequest({ processNextChunk() }, myUploadFileChunkIntervalMillis)
    }

    fun processNextChunk() {
      assert(!myFutureResult.isDone)

      // Add entry right away (simulate behavior of device upload, where an empty file is immediately created on upload)
      if (myCreatedEntry == null) {
        try {
          myFileLength = Files.size(myPath)
          myCreatedEntry = myEntry.addFile(myFileName)
        } catch (e: AdbShellCommandException) {
          doneWithError(e)
          return
        } catch (e: IOException) {
          doneWithError(e)
          return
        }
      }

      // Report progress
      val currentOffset = myCurrentOffset
      service.edtExecutor.execute { myProgress.progress(currentOffset, myFileLength) }

      // Write bytes and enqueue next request if not done yet
      if (myCurrentOffset < myFileLength) {
        addRequest()
        val chunkSize = Math.min(myUploadChunkSize, myFileLength - myCurrentOffset)
        myCreatedEntry!!.size = myCreatedEntry!!.size + chunkSize
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

    init {
      myFutureResult = SettableFuture.create()
      myAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
      Disposer.register(ApplicationManager.getApplication(), this)
      addRequest()
    }
  }

  init {
    deviceSerialNumber = name
    this.root = createRoot(this)
    myTaskExectuor = FutureCallbackExecutor(taskExecutor)
  }
}