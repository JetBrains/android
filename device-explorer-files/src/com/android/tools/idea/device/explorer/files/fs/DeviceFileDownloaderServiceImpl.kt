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
package com.android.tools.idea.device.explorer.files.fs

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.AdbCommandRejectedException
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.device.explorer.files.DeviceExplorerFileManager
import com.android.tools.idea.device.explorer.files.adbimpl.AdbDeviceFileSystemService
import com.android.tools.idea.device.explorer.files.adbimpl.AdbPathUtil
import com.android.utils.FileUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
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

  override suspend fun downloadFiles(
    deviceSerialNumber: String,
    onDevicePaths: List<String>,
    downloadProgress: DownloadProgress,
    localDestinationDirectory: Path
  ): Map<String, VirtualFile> {
    if (onDevicePaths.isEmpty()) {
      return emptyMap()
    }
    try {
      val devices = deviceFileSystemService.devices.value
      val deviceFileSystem = devices.find { it.deviceSerialNumber == deviceSerialNumber }
      return doDownload(requireNotNull(deviceFileSystem), onDevicePaths, downloadProgress, localDestinationDirectory)
    } catch (e: AdbCommandRejectedException) {
      throw DeviceFileDownloaderService.FileDownloadFailedException(e)
    }
  }

  override suspend fun deleteFiles(virtualFiles: List<VirtualFile>) {
    virtualFiles.forEach { fileManager.deleteFile(it) }
  }

  // TODO(b/170230430) downloading files seems to trigger indexing.
  private suspend fun doDownload(
    deviceFileSystem: DeviceFileSystem,
    onDevicePaths: List<String>,
    downloadProgress: DownloadProgress,
    localDestinationDirectory: Path
  ): Map<String, VirtualFile> {
    val entries = mapPathsToEntries(deviceFileSystem, onDevicePaths)
    val entryToDeferredFile = withContext(diskIoThread) {
      entries.associate { entry ->
        val localPath = fileManager.getPathForEntry(entry, localDestinationDirectory)
        FileUtils.mkdirs(localPath.parent.toFile())
        entry.fullPath to async { fileManager.downloadFileEntry(entry, localPath, downloadProgress) }
      }
    }
    entryToDeferredFile.values.awaitAll()
    return entryToDeferredFile.mapValues { e -> e.value.await() }
  }

  /**
   * Maps each on-device path to a [DeviceFileEntry]. If the entry doesn't exist the path is skipped.
   */
  private suspend fun mapPathsToEntries(deviceFileSystem: DeviceFileSystem, onDevicePaths: List<String>): List<DeviceFileEntry> {
    if (haveSameParent(onDevicePaths)) {
      val parentPath = AdbPathUtil.getParentPath(onDevicePaths[0])
      val parentEntry =
        try {
          deviceFileSystem.getEntry(parentPath)
        } catch (e: IllegalArgumentException) {
          // if the path is not found getEntry fails with IllegalArgumentException.
          return emptyList()
        }
      return getEntriesFromCommonParent(parentEntry, onDevicePaths)
    } else {
      return coroutineScope {
        onDevicePaths.map {
          async {
            try {
              deviceFileSystem.getEntry(it)
            } catch (e: IllegalArgumentException) {
              // if the path is not found, getEntry fails with IllegalArgumentException; return null and filter below
              null
            }
          }
        }.awaitAll().filterNotNull()
      }
    }
  }

  /**
   * Maps all the paths passed as argument to children of the given [DeviceFileEntry] node.
   * If a path doesn't have a corresponding child, it is skipped.
   */
  private suspend fun getEntriesFromCommonParent(parent: DeviceFileEntry, paths: List<String>): List<DeviceFileEntry> {
    val entries = parent.entries()
    val pathSet = paths.toSet()
    return entries.filter { pathSet.contains(it.fullPath) }
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
