/*
 * Copyright (C) 2021 The Android Open Source Project
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

@file:JvmMultifileClass
@file:JvmName("AdbLibMigrationUtils")

package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.RemoteFileMode
import com.android.adblib.syncRecv
import com.android.adblib.syncSend
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.SyncException
import com.android.ddmlib.SyncService
import com.android.ddmlib.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Migration function for calls to [SyncService.pullFile]
 */
@Deprecated(deprecationMessage, ReplaceWith("AdbDeviceServices.syncSend"))
@WorkerThread
@Throws(IOException::class, SyncException::class, TimeoutException::class)
fun pushFile(device: IDevice,
             localFilename: String,
             remoteFilepath: String,
             monitor: SyncService.ISyncProgressMonitor) {
  throwIfDispatchThread()

  runBlocking {
    mapToSyncException {
      syncSend(device, localFilename, remoteFilepath, monitor)
    }
  }
}

/**
 * Migration function for calls to [SyncService.pushFile]
 */
@Deprecated(deprecationMessage, ReplaceWith("AdbDeviceServices.syncRecv"))
@WorkerThread
@Throws(IOException::class, SyncException::class, TimeoutException::class)
fun pullFile(device: IDevice,
             remoteFilepath: String,
             localFilename: String,
             monitor: SyncService.ISyncProgressMonitor) {
  throwIfDispatchThread()

  return runBlocking {
    mapToSyncException {
      syncRecv(device, remoteFilepath, localFilename, monitor)
    }
  }
}

private suspend fun syncSend(device: IDevice,
                             localFilename: String,
                             remoteFilepath: String,
                             monitor: SyncService.ISyncProgressMonitor) {
  @Suppress("BlockingMethodInNonBlockingContext")
  withContext(ioDispatcher) {
    val localFilePath = Paths.get(localFilename)
    val localFileSize = Files.size(localFilePath)
    val localFileMode = RemoteFileMode.fromPath(localFilePath) ?: RemoteFileMode.DEFAULT
    val localFileDate = Files.getLastModifiedTime(localFilePath)
    val localFileChannel = createOpenFileChannel(localFilePath)
    localFileChannel.use {
      val progress = SyncProgressToISyncProgressMonitor(monitor, localFileSize)
      deviceServices.syncSend(device.toDeviceSelector(),
                              localFileChannel,
                              remoteFilepath,
                              localFileMode,
                              localFileDate,
                              progress)
      localFileChannel.close()
    }
  }
}

private suspend fun syncRecv(device: IDevice,
                             remoteFilepath: String,
                             localFilename: String,
                             monitor: SyncService.ISyncProgressMonitor) {
  withContext(ioDispatcher) {
    val progress = SyncProgressToISyncProgressMonitor(monitor)
    val fileChannel = createNewFileChannel(localFilename)
    fileChannel.use {
      deviceServices.syncRecv(device.toDeviceSelector(), remoteFilepath, fileChannel, progress)
    }
  }
}

/**
 * Maps exceptions throws from the [AdbDeviceSyncServices] methods of `adblib` to the
 * (approximately) equivalent [SyncException] of `ddmlib`.
 *
 * TODO: Map [IOException] and [AdbFailResponseException] to the corresponding [SyncException].
 *       This is not trivial as we don't have enough info in the exceptions thrown
 *       to correctly map to SyncException
 */
internal inline fun <R> mapToSyncException(block: () -> R): R {
  return try {
    block()
  }
  catch (e: CancellationException) {
    throw SyncException(SyncException.SyncError.CANCELED, e)
  }
  catch(e: AdbProtocolErrorException) {
    throw SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR, e)
  }
  catch (e: AdbFailResponseException) {
    throw SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR, e)
  }
}
