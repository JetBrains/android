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

import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.device.explorer.files.DeviceExplorerFileManager
import com.android.tools.idea.device.explorer.files.DeviceExplorerFileManagerImpl
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.android.tools.idea.device.explorer.files.fs.DownloadProgress
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.delete
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.function.Supplier

class MockDeviceExplorerFileManager(
  private val myProject: Project,
  defaultPath: Supplier<Path>
) : DeviceExplorerFileManager, Disposable {
  private val LOGGER = thisLogger()

  private val myFileManagerImpl = DeviceExplorerFileManagerImpl(myProject) { defaultPath.get() }

  private val myDevices: MutableSet<DeviceFileSystem> = HashSet()

  val downloadFileEntryTracker = FutureValuesTracker<DeviceFileEntry>()
  val downloadFileEntryCompletionTracker = FutureValuesTracker<VirtualFile>()
  val openFileInEditorTracker = FutureValuesTracker<Path>()
  var openFileInEditorError: RuntimeException? = null

  override suspend fun downloadFileEntry(entry: DeviceFileEntry, localPath: Path, progress: DownloadProgress): VirtualFile =
    withContext(uiThread) {
      downloadFileEntryTracker.produce(entry)
      myDevices.add(entry.fileSystem)
      try {
        return@withContext myFileManagerImpl.downloadFileEntry(entry, localPath, progress).also {
          downloadFileEntryCompletionTracker.produce(it)
        }
      } catch (t: Throwable) {
        downloadFileEntryCompletionTracker.produceException(t)
        throw t
      }
    }

  override suspend fun deleteFile(virtualFile: VirtualFile) {
    return myFileManagerImpl.deleteFile(virtualFile)
  }

  override fun getPathForEntry(entry: DeviceFileEntry, destinationPath: Path): Path {
    return myFileManagerImpl.getPathForEntry(entry, destinationPath)
  }

  override suspend fun openFile(localPath: Path) {
    openFileInEditorTracker.produce(localPath)
    return when (val error = openFileInEditorError) {
      null -> myFileManagerImpl.openFile(localPath)
      else -> throw error
    }
  }

  override fun getDefaultLocalPathForEntry(entry: DeviceFileEntry): Path {
    return myFileManagerImpl.getDefaultLocalPathForEntry(entry)
  }

  override fun dispose() {
    // Close all editors
    val manager = FileEditorManager.getInstance(myProject)
    for (file in manager.openFiles) {
      manager.closeFile(file)

      // The TestFileEditorManager does not publish events to the message bus,
      // so we do it here to ensure we hit the code in our DeviceExplorerFileManagerImpl class.
      myProject.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(manager, file)
    }

    // Delete local directories associated to test devices
    for (fileSystem in myDevices) {
      val path = myFileManagerImpl.getDefaultLocalPathForDevice(fileSystem)
      try {
        path.delete()
      } catch (t: Throwable) {
        LOGGER.warn("Error deleting local path \"$path\"", t)
      }
    }
    myDevices.clear()
  }
}