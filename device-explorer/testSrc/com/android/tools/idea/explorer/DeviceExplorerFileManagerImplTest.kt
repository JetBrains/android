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

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DownloadProgress
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService
import com.android.tools.idea.testing.runDispatching
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions
import java.nio.file.Path
import java.nio.file.Paths

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
  private lateinit var tempDirTestFixture: TempDirTestFixture

  override fun setUp() {
    super.setUp()

    edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
    taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)

    val downloadPath = FileUtil.createTempDirectory("fileManagerTest", "", true)
    myDeviceExplorerFileManager = DeviceExplorerFileManagerImpl(project) { downloadPath.toPath() }

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
      FileUtilRt.toSystemDependentName(FileUtilRt.getTempDirectory() + "/fileManagerTest/fileSystem/foo/bar1")
    )

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()
  }

  override fun shouldPerfomThreadingChecks(): Boolean {
    return true
  }

  override fun tearDown() {
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  fun testGetDefaultLocalPathForEntry() {
    val defaultLocalPath = myDeviceExplorerFileManager.getDefaultLocalPathForEntry(fooBar1Entry)
    assertTrue(FileUtil.toSystemIndependentName(defaultLocalPath.toString()).endsWith("/fileSystem/foo/bar1"))
  }

  fun testDownloadFileEntry() = runDispatching(edtExecutor.asCoroutineDispatcher()) {
    // Setup
    val downloadProgress = mock(DownloadProgress::class.java)
    val orderVerifier = inOrder(downloadProgress)

    // Act
    val virtualFile = myDeviceExplorerFileManager.downloadFileEntry(fooBar1Entry, fooBar1LocalPath, downloadProgress)

    // Assert
    assertTrue(virtualFile.path.endsWith("/foo/bar1"))

    orderVerifier.verify(downloadProgress).onStarting("/foo/bar1")
    orderVerifier.verify(downloadProgress).onProgress("/foo/bar1", 0, 0)
    orderVerifier.verify(downloadProgress).onCompleted("/foo/bar1")
    verifyNoMoreInteractions(downloadProgress)
  }

  fun testDeleteFile() = runDispatching {
    // Prepare
    val fileToDelete = tempDirTestFixture.createFile("newfile")

    // Act
    myDeviceExplorerFileManager.deleteFile(fileToDelete)

    // Assert
    assertFalse(fileToDelete.exists())
  }
}
