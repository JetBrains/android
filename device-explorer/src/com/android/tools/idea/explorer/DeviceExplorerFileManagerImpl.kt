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

import com.android.tools.idea.explorer.options.DeviceFileExplorerSettings.Companion.getInstance
import com.android.tools.idea.explorer.options.DeviceFileExplorerSettings.downloadLocation
import com.android.tools.idea.explorer.fs.DeviceFileSystem.name
import com.android.tools.idea.explorer.fs.DeviceFileEntry.fileSystem
import com.android.tools.idea.explorer.fs.DownloadProgress.onStarting
import com.android.tools.idea.explorer.fs.DeviceFileEntry.fullPath
import com.android.tools.idea.explorer.fs.DeviceFileEntry.downloadFile
import com.android.tools.idea.explorer.fs.DownloadProgress.onCompleted
import com.android.tools.idea.explorer.fs.DownloadProgress.onProgress
import com.android.tools.idea.explorer.fs.DownloadProgress.isCancelled
import com.android.tools.idea.explorer.fs.DeviceFileSystem.getEntry
import com.android.tools.idea.explorer.fs.DeviceFileEntry.parent
import com.android.tools.idea.explorer.fs.DeviceFileEntry.name
import com.android.tools.idea.explorer.DeviceExplorerFileManager
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.intellij.util.concurrency.EdtExecutorService
import com.android.tools.idea.explorer.options.DeviceFileExplorerSettings
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.ExecutorUtil
import java.lang.Runnable
import com.intellij.openapi.vfs.VfsUtil
import kotlin.Throws
import java.io.IOException
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.DeviceExplorerFilesUtils
import com.android.tools.idea.explorer.fs.DownloadProgress
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.PathUtilRt
import org.jetbrains.ide.PooledThreadExecutor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * Abstraction over the application logic of the Device Explorer UI
 */
class DeviceExplorerFileManagerImpl @NonInjectable @VisibleForTesting constructor(
  private val myProject: Project,
  edtExecutor: Executor,
  taskExecutor: Executor,
  downloadPathSupplier: Supplier<Path>
) : DeviceExplorerFileManager {
  private val myEdtExecutor: FutureCallbackExecutor
  private val myTaskExecutor: FutureCallbackExecutor
  private val myDefaultDownloadPath: Supplier<Path>

  private constructor(project: Project) : this(
    project,
    FutureCallbackExecutor(EdtExecutorService.getInstance()),
    FutureCallbackExecutor(PooledThreadExecutor.INSTANCE),
    Supplier<Path> { Paths.get(getInstance().downloadLocation) }
  ) {
  }

  fun getDefaultLocalPathForDevice(device: DeviceFileSystem): Path {
    val rootPath = defaultDownloadPath
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
  ): ListenableFuture<VirtualFile?> {
    val futureResult = SettableFuture.create<VirtualFile?>()
    FileUtils.mkdirs(localPath.parent.toFile())
    ExecutorUtil.executeInWriteSafeContextWithAnyModality(myProject, myEdtExecutor) {

      // findFileByIoFile should be called from the write thread, in a write-safe context
      val virtualFile = VfsUtil.findFileByIoFile(localPath.toFile(), true)
      ApplicationManager.getApplication().runWriteAction {
        if (virtualFile != null) {
          try {
            // must be called from a write action
            deleteVirtualFile(virtualFile)
          } catch (exception: Throwable) {
            futureResult.setException(exception)
            return@runWriteAction
          }
        }
        myTaskExecutor.addCallback(downloadFile(entry, localPath, progress), object : FutureCallback<VirtualFile?> {
          override fun onSuccess(result: VirtualFile?) {
            futureResult.set(result)
          }

          override fun onFailure(throwable: Throwable) {
            futureResult.setException(throwable)
          }
        })
      }
    }
    return futureResult
  }

  override fun deleteFile(virtualFile: VirtualFile): ListenableFuture<Unit>? {
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

    // This assertions prevent regressions for b/141649841.
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
    val downloadFileFuture = entry.downloadFile(localPath, fileTransferProgress)
    val getVirtualFile = myTaskExecutor.transformAsync(
      downloadFileFuture,
      AsyncFunction { aVoid: Unit -> DeviceExplorerFilesUtils.findFile(myProject, myEdtExecutor, localPath) }
    )
    myEdtExecutor.addCallback(getVirtualFile, object : FutureCallback<VirtualFile?> {
      override fun onSuccess(virtualFile: VirtualFile?) {
        progress.onCompleted(entry.fullPath)
      }

      override fun onFailure(t: Throwable) {
        progress.onCompleted(entry.fullPath)
        deleteTemporaryFile(localPath)
      }
    })
    return getVirtualFile
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

  private fun mapPathsToEntries(fileSystem: DeviceFileSystem, paths: List<String>): ListenableFuture<List<DeviceFileEntry>> {
    val entries: MutableList<DeviceFileEntry> = ArrayList()
    val allDone = myTaskExecutor.executeFuturesInSequence(paths.iterator()) { path: String? ->
      val futureEntry = fileSystem.getEntry(
        path!!
      )
      myTaskExecutor.transform(futureEntry) { entry: DeviceFileEntry ->
        entries.add(entry)
        Unit
      }
    }
    return myTaskExecutor.transform(allDone) { aVoid: Unit -> entries }
  }

  private val defaultDownloadPath: Path
    private get() = myDefaultDownloadPath.get()

  override fun getPathForEntry(file: DeviceFileEntry, destinationPath: Path): Path {
    val entryPathComponents: MutableList<String?> = ArrayList()
    var entry: DeviceFileEntry? = file
    while (entry != null) {
      entryPathComponents.add(mapName(entry.name))
      entry = entry.parent
    }
    Collections.reverse(entryPathComponents)
    var entryDestinationPath = destinationPath
    for (name in entryPathComponents) {
      entryDestinationPath = entryDestinationPath.resolve(name)
    }
    return entryDestinationPath
  }

  companion object {
    private val LOGGER = Logger.getInstance(
      DeviceExplorerFileManagerImpl::class.java
    )

    private fun mapName(name: String): String {
      return PathUtilRt.suggestFileName(name,  /*allowDots*/true,  /*allowSpaces*/true)
    }

    private fun deleteTemporaryFile(localPath: Path) {
      try {
        Files.deleteIfExists(localPath)
      } catch (e: IOException) {
        LOGGER.warn(String.format("Error deleting device file from local file system \"%s\"", localPath), e)
      }
    }
  }

  init {
    myProject.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (VfsUtilCore.isAncestor(defaultDownloadPath.toFile(), VfsUtilCore.virtualToIoFile(file), true)) {
          val localPath = Paths.get(file.path)
          deleteTemporaryFile(localPath)
        }
      }
    })
    myEdtExecutor = FutureCallbackExecutor(edtExecutor)
    myTaskExecutor = FutureCallbackExecutor(taskExecutor)
    myDefaultDownloadPath = downloadPathSupplier
  }
}