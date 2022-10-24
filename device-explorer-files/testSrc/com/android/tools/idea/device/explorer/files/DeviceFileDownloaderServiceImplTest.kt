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
package com.android.tools.idea.device.explorer.files

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.explorer.files.fs.DownloadProgress
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileEntry
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileSystem
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileSystemService
import com.android.tools.idea.device.explorer.files.fs.DeviceFileDownloaderServiceImpl
import com.android.tools.idea.testing.runDispatching
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.InOrder
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import java.nio.file.Path
import java.nio.file.Paths

class DeviceFileDownloaderServiceImplTest : AndroidTestCase() {

  private lateinit var deviceFileDownloaderService: DeviceFileDownloaderServiceImpl
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var taskExecutor: FutureCallbackExecutor

  private lateinit var mockDeviceFileSystemService: MockDeviceFileSystemService
  private lateinit var mockDeviceFileSystem: MockDeviceFileSystem
  private lateinit var rootEntry: MockDeviceFileEntry
  private lateinit var emptyDirEntry: MockDeviceFileEntry
  private lateinit var fooDirEntry: MockDeviceFileEntry
  private lateinit var fooBar1Entry: MockDeviceFileEntry
  private lateinit var fooBar2Entry: MockDeviceFileEntry
  private lateinit var foo2DirEntry: MockDeviceFileEntry
  private lateinit var foo2Bar1Entry: MockDeviceFileEntry
  private lateinit var foo2Bar2Entry: MockDeviceFileEntry

  private lateinit var fooBar1LocalPath: Path

  private lateinit var progress: DownloadProgress
  private lateinit var orderVerifier: InOrder

  private lateinit var downloadPath: Path

  override fun setUp() {
    super.setUp()

    edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
    taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)

    downloadPath = FileUtil.createTempDirectory("fileManagerTest", "", true).toPath()
    val myDeviceExplorerFileManager = DeviceExplorerFileManagerImpl(project) { downloadPath }

    mockDeviceFileSystemService = MockDeviceFileSystemService(project, edtExecutor, taskExecutor)
    mockDeviceFileSystem = mockDeviceFileSystemService.addDevice("fileSystem")

    deviceFileDownloaderService = DeviceFileDownloaderServiceImpl(project, mockDeviceFileSystemService, myDeviceExplorerFileManager)

    rootEntry = mockDeviceFileSystem.root

    emptyDirEntry = rootEntry.addDirectory("empty")

    fooDirEntry = rootEntry.addDirectory("foo")
    fooBar1Entry = fooDirEntry.addFile("bar1")
    fooBar2Entry = fooDirEntry.addFile("bar2")

    foo2DirEntry = rootEntry.addDirectory("foo2")
    foo2Bar1Entry = foo2DirEntry.addFile("bar1")
    foo2Bar2Entry = foo2DirEntry.addFile("bar2")

    fooBar1LocalPath = Paths.get(
      FileUtil.toSystemDependentName(FileUtilRt.getTempDirectory() + "/fileManagerTest/fileSystem/foo/bar1")
    )

    mockDeviceFileSystem.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    mockDeviceFileSystem.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk

    fooBar1Entry.size = 2000
    fooBar2Entry.size = 2000

    foo2Bar1Entry.size = 2000
    foo2Bar1Entry.size = 2000

    progress = mock()
    orderVerifier = inOrder(progress)
  }

  fun testDownloadFilesFromSameDir() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Act
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      listOf("/foo/bar1", "/foo/bar2"),
      progress,
      downloadPath
    )

    // Assert
    assertTrue(virtualFiles.getValue("/foo/bar1").path.endsWith("/foo/bar1"))
    assertTrue(virtualFiles.getValue("/foo/bar2").path.endsWith("/foo/bar2"))

    verify(progress).onStarting("/foo/bar1")
    verify(progress).onStarting("/foo/bar2")
    verify(progress).onProgress("/foo/bar1", 0, 2000)
    verify(progress).onProgress("/foo/bar2", 0, 2000)
    verify(progress).onProgress("/foo/bar1", 2000, 2000)
    verify(progress).onProgress("/foo/bar2", 2000, 2000)
    verify(progress).onCompleted("/foo/bar1")
    verify(progress).onCompleted("/foo/bar2")
  }

  fun testDownloadFilesFromDifferentDir() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Act
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      listOf("/foo/bar1", "/foo2/bar1"),
      progress,
      downloadPath
    )

    // Assert
    assertTrue(virtualFiles.getValue("/foo/bar1").path.endsWith("/foo/bar1"))
    assertTrue(virtualFiles.getValue("/foo2/bar1").path.endsWith("/foo2/bar1"))

    verify(progress).onProgress("/foo/bar1", 0, 2000)
    verify(progress).onProgress("/foo2/bar1", 0, 2000)
    verify(progress).onProgress("/foo/bar1", 2000, 2000)
    verify(progress).onProgress("/foo2/bar1", 2000, 2000)
  }

  fun testDownloadFilesMissingFile() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Act
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      listOf("/foo/bar1", "/foo/barMissing"),
      progress,
      downloadPath
    )

    // Assert
    assertTrue(virtualFiles.getValue("/foo/bar1").path.endsWith("/foo/bar1"))
    assertEquals(1, virtualFiles.size)

    orderVerifier.verify(progress).onProgress("/foo/bar1", 0, 2000)
    orderVerifier.verify(progress).onProgress("/foo/bar1", 2000, 2000)
  }

  fun testDownloadFilesMissingDir() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Act
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      listOf("/foo/bar1", "/missingDir/bar"),
      progress,
      downloadPath
    )

    // Assert
    assertTrue(virtualFiles.getValue("/foo/bar1").path.endsWith("/foo/bar1"))
    assertEquals(1, virtualFiles.size)

    orderVerifier.verify(progress).onProgress("/foo/bar1", 0, 2000)
    orderVerifier.verify(progress).onProgress("/foo/bar1", 2000, 2000)
  }

  fun testDownloadEmptyList() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Act
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      emptyList(),
      progress,
      downloadPath
    )

    // Assert
    assertEquals(0, virtualFiles.size)
  }

  fun testDeleteFile() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Prepare
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      listOf("/foo/bar1"),
      progress,
      downloadPath
    )
    val fileToDelete = virtualFiles.getValue("/foo/bar1")

    // Act
    deviceFileDownloaderService.deleteFiles(listOf(fileToDelete))

    // Assert
    assertFalse(fileToDelete.exists())
  }

  fun testDeleteMultipleFiles() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Prepare
    val virtualFiles = deviceFileDownloaderService.downloadFiles(
      "fileSystem",
      listOf("/foo/bar1", "/foo/bar2"),
      progress,
      downloadPath
    )
    val filesToDelete = virtualFiles.values.toList()

    // Act
    deviceFileDownloaderService.deleteFiles(filesToDelete)

    // Assert
    filesToDelete.forEach { assertFalse(it.exists()) }
  }
}
