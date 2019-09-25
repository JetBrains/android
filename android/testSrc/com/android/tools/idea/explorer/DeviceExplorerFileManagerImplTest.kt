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

import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.deviceExplorer.FileHandler
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.AsyncTestUtils
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier

class DeviceExplorerFileManagerImplTest : AndroidTestCase() {

  private lateinit var myDeviceExplorerFileManager: DeviceExplorerFileManager
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

  override fun setUp() {
    super.setUp()

    edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
    taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)

    val downloadPath = FileUtil.createTempDirectory("fileManagerTest", "", true)
    myDeviceExplorerFileManager = DeviceExplorerFileManagerImpl(project, edtExecutor, edtExecutor, Supplier<Path> { downloadPath.toPath() })

    mockDeviceFileSystemService = MockDeviceFileSystemService(project, edtExecutor, taskExecutor)
    mockDeviceFileSystem = mockDeviceFileSystemService.addDevice("fileSystem")
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
  }

  fun testGetDefaultLocalPathForEntry() {
    val defaultLocalPath = myDeviceExplorerFileManager.getDefaultLocalPathForEntry(fooBar1Entry)
    assertTrue(FileUtil.toSystemIndependentName(defaultLocalPath.toString()).endsWith("/fileSystem/foo/bar1"))
  }

  fun testDownloadFileEntry() {
    // Setup
    val downloadProgress = mock(DownloadProgress::class.java)
    val orderVerifier = inOrder(downloadProgress)

    // Act
    val downloadEntryFuture = myDeviceExplorerFileManager.downloadFileEntry(fooBar1Entry, fooBar1LocalPath, downloadProgress)
    val (deviceFileId, virtualFile, additionalFiles) = AsyncTestUtils.pumpEventsAndWaitForFuture(downloadEntryFuture)

    // Assert
    assertEquals(deviceFileId.deviceId, mockDeviceFileSystem.name)
    assertEquals("/foo/bar1", FileUtil.toSystemIndependentName(deviceFileId.devicePath))
    assertEquals("bar1", virtualFile.name)
    assertEmpty(additionalFiles)

    orderVerifier.verify(downloadProgress).onStarting(Paths.get("/foo/bar1"))
    orderVerifier.verify(downloadProgress).onProgress(Paths.get("/foo/bar1"), 0, 0)
    orderVerifier.verify(downloadProgress).onCompleted(Paths.get("/foo/bar1"))
    verifyNoMoreInteractions(downloadProgress)
  }

  fun testDownloadFileEntryAdditionalEntriesSameParent() {
    // Setup
    val mockFileHandler = mock(FileHandler::class.java)
    `when`(mockFileHandler.getAdditionalDevicePaths(eq("/foo/bar1"), any(VirtualFile::class.java))).thenReturn(listOf("/foo/bar2"))
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), FileHandler.EP_NAME, mockFileHandler, testRootDisposable)

    val downloadProgress = mock(DownloadProgress::class.java)

    val orderVerifier = Mockito.inOrder(downloadProgress)

    // Act
    val future = myDeviceExplorerFileManager.downloadFileEntry(fooBar1Entry, fooBar1LocalPath, downloadProgress)
    val (_, _, additionalFiles) = AsyncTestUtils.pumpEventsAndWaitForFuture(future)

    // Assert
    verify(mockFileHandler).getAdditionalDevicePaths(
      eq("/foo/bar1"),
      argThat(Utils.VirtualFilePathArgumentMatcher("/foo/bar1"))
    )

    assertTrue(Utils.VirtualFilePathArgumentMatcher("/foo/bar2").matches(additionalFiles.first()))

    orderVerifier.verify(downloadProgress).onStarting(Paths.get("/foo/bar1"))
    orderVerifier.verify(downloadProgress).onProgress(Paths.get("/foo/bar1"), 0, 0)
    orderVerifier.verify(downloadProgress).onCompleted(Paths.get("/foo/bar1"))

    orderVerifier.verify(downloadProgress).onStarting(Paths.get("/foo/bar2"))
    orderVerifier.verify(downloadProgress).onProgress(Paths.get("/foo/bar2"), 0, 0)
    orderVerifier.verify(downloadProgress).onCompleted(Paths.get("/foo/bar2"))
  }

  fun testDownloadFileEntryAdditionalEntriesDifferentParent() {
    // Setup
    val fileHandler = FileHandler { _, _ -> listOf("/foo2/bar1") }
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), FileHandler.EP_NAME, fileHandler, testRootDisposable)

    val downloadProgress = mock(DownloadProgress::class.java)

    // Act
    val future = myDeviceExplorerFileManager.downloadFileEntry(fooBar1Entry, fooBar1LocalPath, downloadProgress)
    val (_, _, additionalFiles) = AsyncTestUtils.pumpEventsAndWaitForFuture(future)

    // Assert
    assertTrue(Utils.VirtualFilePathArgumentMatcher("/foo2/bar1").matches(additionalFiles.first()))
  }
}
