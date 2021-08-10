/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.explorer

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.AdbCommandRejectedException
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.concurrency.transformAsyncNullable
import com.android.tools.idea.concurrency.transformNullable
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import com.android.utils.FileUtils
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.ide.PooledThreadExecutor
import java.nio.file.Path

@UiThread
class DeviceFileDownloaderServiceImpl @NonInjectable @TestOnly constructor(
  private val project: Project,
  private val deviceFileSystemService: DeviceFileSystemService<*>,
  private val fileManager: DeviceExplorerFileManager
) : DeviceFileDownloaderService {

  constructor(project: Project) : this (
    project,
    AdbDeviceFileSystemService.getInstance(project) as DeviceFileSystemService<*>,
    DeviceExplorerFileManager.getInstance(project)
  )

  private val taskExecutor = PooledThreadExecutor.INSTANCE
  private val edtExecutor = EdtExecutorService.getInstance()

  override fun downloadFiles(
    deviceSerialNumber: String,
    onDevicePaths: List<String>,
    downloadProgress: DownloadProgress,
    localDestinationDirectory: Path
  ): ListenableFuture<Map<String, VirtualFile>> {
    if (onDevicePaths.isEmpty()) {
      return Futures.immediateFuture(emptyMap())
    }

    return deviceFileSystemService.start { AndroidSdkUtils.getAdb(project) }.transformAsyncNullable(edtExecutor) {
      deviceFileSystemService.devices.transformAsync(taskExecutor) { devices ->
        val deviceFileSystem = devices!!.find { it.deviceSerialNumber == deviceSerialNumber }
        require(deviceFileSystem != null)
        doDownload(deviceFileSystem, onDevicePaths, downloadProgress, localDestinationDirectory)
      }
    }.catching(edtExecutor, AdbCommandRejectedException::class.java) {
      throw DeviceFileDownloaderService.FileDownloadFailedException(it)
    }
  }

  override fun deleteFiles(virtualFiles: List<VirtualFile>): ListenableFuture<Unit> {
    val futures = virtualFiles.map { fileManager.deleteFile(it).transformNullable(taskExecutor) { Unit } }
    return Futures.whenAllSucceed(futures).call( { Unit }, taskExecutor)
  }

  // TODO(b/170230430) downloading files seems to trigger indexing.
  private fun doDownload(
    deviceFileSystem: DeviceFileSystem,
    onDevicePaths: List<String>,
    downloadProgress: DownloadProgress,
    localDestinationDirectory: Path
  ): ListenableFuture<Map<String, VirtualFile>> {
    val futureEntries = mapPathsToEntries(deviceFileSystem, onDevicePaths)

    return futureEntries.transformAsync(taskExecutor) { entries ->
      val futurePairs = entries.map { entry ->
        val localPath = fileManager.getPathForEntry(entry, localDestinationDirectory)
        FileUtils.mkdirs(localPath.parent.toFile())

        fileManager
          .downloadFileEntry(entry, localPath, downloadProgress)
          .transform(taskExecutor) { file -> Pair(entry.fullPath, file) }
      }

      Futures
        // if a future fails, the AggregateFuture resulting from `allAsList` logs the error
        .allAsList(futurePairs)
        .transform(taskExecutor) { pairs -> pairs.map { it.first to it.second }.toMap() }
    }
  }

  /**
   * Maps each on-device path to a [DeviceFileEntry]. If the entry doesn't exist the path is skipped.
   */
  private fun mapPathsToEntries(deviceFileSystem: DeviceFileSystem, onDevicePaths: List<String>): ListenableFuture<List<DeviceFileEntry>> {
    return if (haveSameParent(onDevicePaths)) {
      val parentPath = AdbPathUtil.getParentPath(onDevicePaths[0])
      val parentEntryFuture = deviceFileSystem.getEntry(parentPath)
      parentEntryFuture
        // if the path is not found getEntry fails with IllegalArgumentException.
        .catching(taskExecutor, IllegalArgumentException::class.java) { null }
        .transformAsyncNullable(taskExecutor) { parentEntry ->
          if (parentEntry == null ) {
            Futures.immediateFuture(emptyList())
          }
          else {
            getEntriesFromCommonParent(parentEntry, onDevicePaths)
          }
        }
    }
    else {
      val futureEntries = onDevicePaths.map { deviceFileSystem.getEntry(it) }
      Futures
        // if the path is not found getEntry fails with IllegalArgumentException.
        .successfulAsList(futureEntries)
        .transform(taskExecutor) { it.filterNotNull() }
    }
  }

  /**
   * Maps all the paths passed as argument to children of the given [DeviceFileEntry] node.
   * If a path doesn't have a corresponding children, it is skipped.
   */
  private fun getEntriesFromCommonParent(parent: DeviceFileEntry, paths: List<String>): ListenableFuture<List<DeviceFileEntry>> {
    return parent
      .entries
      .transform(taskExecutor) { entries ->
        paths.mapNotNull { path -> entries.firstOrNull { it.fullPath == path } }
      }
  }

  /**
   * Returns true if the files corresponding to the paths passed as argument are in the same directory.
   */
  private fun haveSameParent(paths: List<String>): Boolean {
    if (paths.isEmpty()) return false

    val firstPath = paths[0]
    val parentPath = AdbPathUtil.getParentPath(firstPath)
    return paths.none { AdbPathUtil.getParentPath(it) != parentPath }
  }
}
