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

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.runWriteActionAndWait
import com.android.tools.idea.device.explorer.files.DeviceExplorerFilesUtils.findFile
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.android.tools.idea.device.explorer.files.fs.DownloadProgress
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import com.android.tools.idea.device.explorer.files.options.DeviceFileExplorerSettings
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.ex.FileTypeChooser
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.PathUtilRt
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


/**
 * Default implementation of [DeviceExplorerFileManager] that integrates with
 * [VirtualFile], [com.intellij.openapi.fileEditor.FileEditorManager] and
 * [com.intellij.openapi.application.Application.runWriteAction]
 */
class DeviceExplorerFileManagerImpl @NonInjectable @VisibleForTesting constructor(
  private val project: Project,
  private val defaultDownloadPathSupplier: () -> Path
) : DeviceExplorerFileManager {
  private val LOGGER = thisLogger()

  private val temporaryEditorFiles = mutableListOf<VirtualFile>()

  init {
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorManagerAdapter())
  }

  /** Service constructor */
  private constructor(project: Project) : this(
    project,
    defaultDownloadPathSupplier = { Paths.get(DeviceFileExplorerSettings.getInstance().downloadLocation) }
  )

  fun getDefaultLocalPathForDevice(device: DeviceFileSystem): Path {
    val rootPath = defaultDownloadPathSupplier()
    return rootPath.resolve(mapName(device.name))
  }

  override fun getDefaultLocalPathForEntry(entry: DeviceFileEntry): Path {
    val devicePath = getDefaultLocalPathForDevice(entry.fileSystem)
    return getPathForEntry(entry, devicePath)
  }

  override suspend fun downloadFileEntry(
    entry: DeviceFileEntry,
    localPath: Path,
    progress: DownloadProgress
  ): VirtualFile {
    withContext(diskIoThread) {
      FileUtils.mkdirs(localPath.parent.toFile())
    }
    return withWriteSafeContextWithCurrentModality {
      // findFileByIoFile should be called from the write thread, in a write-safe context
      VfsUtil.findFileByIoFile(localPath.toFile(), true)?.let {
        runWriteActionAndWait {
          // must be called from a write action
          deleteVirtualFile(it)
        }
      }
      downloadFile(entry, localPath, progress)
    }
  }

  override suspend fun deleteFile(virtualFile: VirtualFile) {
    withWriteSafeContextWithCurrentModality {
      ApplicationManager.getApplication().runWriteAction {
        // must be called from a write action
        deleteVirtualFile(virtualFile)
      }
    }
  }

  @Throws(IOException::class)
  private fun deleteVirtualFile(virtualFile: VirtualFile) {
    // Using VFS to delete files has the advantage of throwing VFS events,
    // so listeners can react to actions on the files - for example by closing a file before it being deleted.

    // This assertion prevents regressions for b/141649841.
    // We need to add this assertion because in tests the deletion of a file doesn't trigger some PSI events that call the assertion.
    (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
    virtualFile.delete(this)
  }

  /**
   * Downloads the file corresponding to the [DeviceFileEntry] passed as argument, to the local path specified.
   * @param entry The entry corresponding to the file to download.
   * @param localPath Where to download the file.
   * @param progress Progress indicator for the download operation.
   */
  @UiThread
  private suspend fun downloadFile(
    entry: DeviceFileEntry,
    localPath: Path,
    progress: DownloadProgress
  ): VirtualFile {
    val fileTransferProgress = createFileTransferProgress(entry, progress)
    progress.onStarting(entry.fullPath)
    try {
      entry.downloadFile(localPath, fileTransferProgress)
      return findFile(localPath)
    } catch (t: Throwable) {
      deleteTemporaryFile(localPath)
      throw t
    } finally {
      progress.onCompleted(entry.fullPath)
    }
  }

  private fun createFileTransferProgress(entry: DeviceFileEntry, progress: DownloadProgress): FileTransferProgress {
    return object : FileTransferProgress {
      @UiThread
      override fun progress(currentBytes: Long, totalBytes: Long) {
        progress.onProgress(entry.fullPath, currentBytes, totalBytes)
      }

      @WorkerThread
      override fun isCancelled(): Boolean {
        return progress.isCancelled()
      }
    }
  }

  override fun getPathForEntry(file: DeviceFileEntry, destinationPath: Path): Path {
    val entryPathComponents: MutableList<String> = ArrayList()
    var entry: DeviceFileEntry? = file
    while (entry != null) {
      entryPathComponents.add(mapName(entry.name))
      entry = entry.parent
    }
    entryPathComponents.reverse()
    var entryDestinationPath = destinationPath
    for (name in entryPathComponents) {
      entryDestinationPath = entryDestinationPath.resolve(name)
    }
    return entryDestinationPath
  }

  private fun mapName(name: String): String {
    return PathUtilRt.suggestFileName(name,  /*allowDots*/true,  /*allowSpaces*/true)
  }

  private fun deleteTemporaryFile(localPath: Path) {
    try {
      Files.deleteIfExists(localPath)
    } catch (e: IOException) {
      LOGGER.warn("Error deleting device file from local file system \"$localPath\"", e)
    }
  }

  override suspend fun openFile(localPath: Path) {
    val file = findFile(localPath)
    withContext(uiThread) {
      FileTypeChooser.getKnownFileTypeOrAssociate(file, project) ?: cancelAndThrow()
      OpenFileAction.openFile(file, project)
      temporaryEditorFiles.add(file)
    }
  }

  private inner class MyFileEditorManagerAdapter : FileEditorManagerListener {
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
      if (temporaryEditorFiles.contains(file)) {
        temporaryEditorFiles.remove(file)
        val localPath = Paths.get(file.path)
        deleteTemporaryFile(localPath)
      }
    }
  }
}
