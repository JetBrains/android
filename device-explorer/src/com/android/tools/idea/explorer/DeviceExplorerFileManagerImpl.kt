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
package com.android.tools.idea.explorer

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.explorer.DeviceExplorerFilesUtils.findFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DownloadProgress
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.options.DeviceFileExplorerSettings
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
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
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor


/**
 * Abstraction over the application logic of the Device Explorer UI
 */
class DeviceExplorerFileManagerImpl @NonInjectable @VisibleForTesting constructor(
  private val myProject: Project,
  edtExecutor: Executor,
  taskExecutor: Executor,
  private val defaultDownloadPathSupplier: () -> Path
) : DeviceExplorerFileManager {
  private val LOGGER = thisLogger()

  private val myTemporaryEditorFiles = mutableListOf<VirtualFile>()
  private val myEdtExecutor = FutureCallbackExecutor(edtExecutor)
  private val myTaskExecutor = FutureCallbackExecutor(taskExecutor)

  init {
    myProject.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorManagerAdapter())
  }

  /** Service constructor */
  private constructor(project: Project) : this(
    project,
    edtExecutor = EdtExecutorService.getInstance(),
    taskExecutor = PooledThreadExecutor.INSTANCE,
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

  override fun downloadFileEntry(
    entry: DeviceFileEntry,
    localPath: Path,
    progress: DownloadProgress
  ): ListenableFuture<VirtualFile> {
    try {
      FileUtils.mkdirs(localPath.parent.toFile())
    } catch (exception: Throwable) {
      return immediateFailedFuture(exception)
    }
    val futureResult = SettableFuture.create<VirtualFile>()
    ExecutorUtil.executeInWriteSafeContextWithAnyModality(myProject, myEdtExecutor) {
      // findFileByIoFile should be called from the write thread, in a write-safe context
      val virtualFile = VfsUtil.findFileByIoFile(localPath.toFile(), true)
      ApplicationManager.getApplication().runWriteAction {
        try {
          if (virtualFile != null) {
            // must be called from a write action
            deleteVirtualFile(virtualFile)
          }
          futureResult.setFuture(downloadFile(entry, localPath, progress))
        } catch (exception: Throwable) {
          futureResult.setException(exception)
        }
      }
    }
    return futureResult
  }

  override fun deleteFile(virtualFile: VirtualFile): ListenableFuture<Unit> {
    val futureResult = SettableFuture.create<Unit>()
    ExecutorUtil.executeInWriteSafeContextWithAnyModality(myProject, myEdtExecutor) {
      ApplicationManager.getApplication().runWriteAction {
        try {
          // must be called from a write action
          deleteVirtualFile(virtualFile)
          futureResult.set(Unit)
        } catch (exception: Throwable) {
          futureResult.setException(exception)
        }
      }
    }
    return futureResult
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
  private fun downloadFile(
    entry: DeviceFileEntry,
    localPath: Path,
    progress: DownloadProgress
  ): ListenableFuture<VirtualFile> {
    val fileTransferProgress = createFileTransferProgress(entry, progress)
    progress.onStarting(entry.fullPath)
    return entry.downloadFile(localPath, fileTransferProgress)
      .transformAsync(myTaskExecutor) {
        DeviceExplorerFilesUtils.findFile(myProject, myEdtExecutor, localPath)
      }.also {
        it.addCallback(myEdtExecutor,
                       success = { progress.onCompleted(entry.fullPath) },
                       failure = {
                         progress.onCompleted(entry.fullPath)
                         deleteTemporaryFile(localPath)
                       })
      }
  }

  private fun createFileTransferProgress(entry: DeviceFileEntry, progress: DownloadProgress): FileTransferProgress {
    return object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        progress.onProgress(entry.fullPath, currentBytes, totalBytes)
      }

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

  override fun openFile(localPath: Path): ListenableFuture<Void> {
    return openFileInEditorWorker(localPath)
  }

  private fun openFileInEditorWorker(localPath: Path): ListenableFuture<Void> {
    val futureFile = findFile(myProject, myEdtExecutor, localPath)
    return myEdtExecutor.transform(futureFile) { file: VirtualFile ->
      FileTypeChooser.getKnownFileTypeOrAssociate(file, myProject) ?: throw CancellationException("Operation cancelled by user")
      OpenFileAction.openFile(file, myProject)
      myTemporaryEditorFiles.add(file)
      null
    }
  }

  private inner class MyFileEditorManagerAdapter : FileEditorManagerListener {
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
      if (myTemporaryEditorFiles.contains(file)) {
        myTemporaryEditorFiles.remove(file)
        val localPath = Paths.get(file.path)
        deleteTemporaryFile(localPath)
      }
    }
  }
}
