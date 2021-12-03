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

import com.android.tools.idea.explorer.fs.DeviceFileEntry.fileSystem
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl.downloadFileEntry
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl.deleteFile
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl.getPathForEntry
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl.openFile
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl.getDefaultLocalPathForEntry
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl.getDefaultLocalPathForDevice
import com.intellij.util.io.delete
import com.android.tools.idea.explorer.DeviceExplorerFileManager
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DownloadProgress
import java.lang.RuntimeException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerFileManager
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.util.Arrays
import java.util.HashSet
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.Supplier

class MockDeviceExplorerFileManager(
  private val myProject: Project,
  edtExecutor: Executor,
  taskExecutor: Executor,
  defaultPath: Supplier<Path?>
) : DeviceExplorerFileManager, Disposable {
  private val myFileManagerImpl: DeviceExplorerFileManagerImpl
  private val myEdtExecutor: FutureCallbackExecutor
  private val myDevices: MutableSet<DeviceFileSystem> = HashSet()
  val downloadFileEntryTracker = FutureValuesTracker<DeviceFileEntry>()
  val downloadFileEntryCompletionTracker = FutureValuesTracker<VirtualFile>()
  val openFileInEditorTracker = FutureValuesTracker<Path>()
  private var myOpenFileInEditorError: RuntimeException? = null
  override fun downloadFileEntry(entry: DeviceFileEntry, localPath: Path, progress: DownloadProgress): ListenableFuture<VirtualFile> {
    downloadFileEntryTracker.produce(entry)
    myDevices.add(entry.fileSystem)
    val futureResult = myFileManagerImpl.downloadFileEntry(entry, localPath, progress)
    myEdtExecutor.addCallback(futureResult, object : FutureCallback<VirtualFile?> {
      override fun onSuccess(result: VirtualFile?) {
        downloadFileEntryCompletionTracker.produce(result)
      }

      override fun onFailure(t: Throwable) {
        downloadFileEntryCompletionTracker.produceException(t)
      }
    })
    return futureResult
  }

  override fun deleteFile(virtualFile: VirtualFile): ListenableFuture<Unit> {
    return myFileManagerImpl.deleteFile(virtualFile)
  }

  override fun getPathForEntry(entry: DeviceFileEntry, destinationPath: Path): Path {
    return myFileManagerImpl.getPathForEntry(entry, destinationPath)
  }

  override fun openFile(localPath: Path): ListenableFuture<Void> {
    openFileInEditorTracker.produce(localPath)
    return if (myOpenFileInEditorError != null) {
      Futures.immediateFailedFuture(myOpenFileInEditorError)
    } else myFileManagerImpl.openFile(localPath)
  }

  override fun getDefaultLocalPathForEntry(entry: DeviceFileEntry): Path {
    return myFileManagerImpl.getDefaultLocalPathForEntry(entry)
  }

  override fun dispose() {
    // Close all editors
    val manager = FileEditorManager.getInstance(myProject)
    Arrays.stream(manager.openFiles).forEach { file: VirtualFile? ->
      manager.closeFile(file!!)

      // The TestFileEditorManager does not publish events to the message bus,
      // so we do it here to ensure we hit the code in our DeviceExplorerFileManagerImpl class.
      myProject.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(manager, file)
    }

    // Delete local directories associated to test devices
    myDevices.forEach(Consumer { fileSystem: DeviceFileSystem? ->
      val path = myFileManagerImpl.getDefaultLocalPathForDevice(fileSystem!!)
      try {
        path.delete()
      } catch (t: Throwable) {
        LOGGER.warn(String.format("Error deleting local path \"%s\"", path), t)
      }
    })
    myDevices.clear()
  }

  fun setOpenFileInEditorError(e: RuntimeException?) {
    myOpenFileInEditorError = e
  }

  companion object {
    private val LOGGER = Logger.getInstance(
      MockDeviceExplorerFileManager::class.java
    )
  }

  init {
    myEdtExecutor = FutureCallbackExecutor(edtExecutor)
    myFileManagerImpl = DeviceExplorerFileManagerImpl(myProject, edtExecutor, taskExecutor) { defaultPath.get()!! }
  }
}