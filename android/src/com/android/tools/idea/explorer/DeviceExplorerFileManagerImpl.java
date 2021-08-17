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
package com.android.tools.idea.explorer;

import static com.android.tools.idea.explorer.ExecutorUtil.executeInWriteSafeContextWithAnyModality;

import com.android.tools.idea.concurrency.FutureCallbackExecutor;
import com.android.tools.idea.device.fs.DeviceFileId;
import com.android.tools.idea.device.fs.DownloadProgress;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.android.tools.idea.explorer.options.DeviceFileExplorerSettings;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.PathUtilRt;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;


/**
 * Abstraction over the application logic of the Device Explorer UI
 */
public class DeviceExplorerFileManagerImpl implements DeviceExplorerFileManager {
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerFileManagerImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final FutureCallbackExecutor myTaskExecutor;
  @NotNull private Supplier<Path> myDefaultDownloadPath;

  private DeviceExplorerFileManagerImpl(@NotNull Project project) {
    this(
      project,
      new FutureCallbackExecutor(EdtExecutorService.getInstance()),
      new FutureCallbackExecutor(PooledThreadExecutor.INSTANCE),
         () -> Paths.get(DeviceFileExplorerSettings.getInstance().getDownloadLocation())
    );
  }

  @NonInjectable
  @VisibleForTesting
  public DeviceExplorerFileManagerImpl(@NotNull Project project,
                                       @NotNull Executor edtExecutor,
                                       @NotNull Executor taskExecutor,
                                       @NotNull Supplier<Path> downloadPathSupplier) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (VfsUtilCore.isAncestor(getDefaultDownloadPath().toFile(), VfsUtilCore.virtualToIoFile(file), true)) {
          Path localPath = Paths.get(file.getPath());
          deleteTemporaryFile(localPath);
        }
      }
    });

    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
    myTaskExecutor = new FutureCallbackExecutor(taskExecutor);
    myDefaultDownloadPath = downloadPathSupplier;
  }

  @NotNull
  public Path getDefaultLocalPathForDevice(@NotNull DeviceFileSystem device) {
    Path rootPath = getDefaultDownloadPath();
    return rootPath.resolve(mapName(device.getName()));
  }

  @NotNull
  @Override
  public Path getDefaultLocalPathForEntry(@NotNull DeviceFileEntry entry) {
    Path devicePath = getDefaultLocalPathForDevice(entry.getFileSystem());
    return getPathForEntry(entry, devicePath);
  }

  @NotNull
  @Override
  public ListenableFuture<VirtualFile> downloadFileEntry(@NotNull DeviceFileEntry entry,
                                                         @NotNull Path localPath,
                                                         @NotNull DownloadProgress progress) {
    SettableFuture<VirtualFile> futureResult = SettableFuture.create();
    FileUtils.mkdirs(localPath.getParent().toFile());

    executeInWriteSafeContextWithAnyModality(myProject, myEdtExecutor, () -> {
      // findFileByIoFile should be called from the write thread, in a write-safe context
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(localPath.toFile(), true);
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (virtualFile != null) {
          try {
            // must be called from a write action
            deleteVirtualFile(virtualFile);
          } catch (Throwable exception) {
            futureResult.setException(exception);
            return;
          }
        }

        myTaskExecutor.addCallback(downloadFile(entry, localPath, progress), new FutureCallback<VirtualFile>() {
          @Override
          public void onSuccess(VirtualFile result) {
            futureResult.set(result);
          }

          @Override
          public void onFailure(@NotNull Throwable throwable) {
            futureResult.setException(throwable);
          }
        });
      });
    });

    return futureResult;
  }

  @Override
  public ListenableFuture<Void> deleteFile(@NotNull VirtualFile virtualFile) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    executeInWriteSafeContextWithAnyModality(myProject, myEdtExecutor, () -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          // must be called from a write action
          deleteVirtualFile(virtualFile);
          futureResult.set(null);
        } catch (Throwable exception) {
          futureResult.setException(exception);
        }
      });
    });

    return futureResult;
  }

  private void deleteVirtualFile(@NotNull VirtualFile virtualFile) throws IOException {
    // Using VFS to delete files has the advantage of throwing VFS events,
    // so listeners can react to actions on the files - for example by closing a file before it being deleted.

    // This assertions prevent regressions for b/141649841.
    // We need to add this assertion because in tests the deletion of a file doesn't trigger some PSI events that call the assertion.
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
    virtualFile.delete(this);
  }

  /**
   * Downloads the file corresponding to the {@link DeviceFileEntry} passed as argument, to the local path specified.
   * @param entry The entry corresponding to the file to download.
   * @param localPath Where to download the file.
   * @param progress Progress indicator for the download operation.
   */
  @NotNull
  private ListenableFuture<VirtualFile> downloadFile(@NotNull DeviceFileEntry entry,
                                                     @NotNull Path localPath,
                                                     @NotNull DownloadProgress progress) {
    FileTransferProgress fileTransferProgress = createFileTransferProgress(entry, progress);
    progress.onStarting(entry.getFullPath());
    ListenableFuture<Void> downloadFileFuture = entry.downloadFile(localPath, fileTransferProgress);
    ListenableFuture<VirtualFile> getVirtualFile = myTaskExecutor.transformAsync(
      downloadFileFuture,
      aVoid -> DeviceExplorerFilesUtils.findFile(myProject, myEdtExecutor, localPath)
    );
    myEdtExecutor.addCallback(getVirtualFile, new FutureCallback<VirtualFile>() {
      @Override
      public void onSuccess(VirtualFile virtualFile) {
        // Set the device/path information on the virtual file so custom editors
        // (e.g. database viewer) know which device this file is coming from.
        DeviceFileId fileInfo = new DeviceFileId(entry.getFileSystem().getName(), entry.getFullPath());
        fileInfo.storeInVirtualFile(virtualFile);

        progress.onCompleted(entry.getFullPath());
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        progress.onCompleted(entry.getFullPath());
        deleteTemporaryFile(localPath);
      }
    });

    return getVirtualFile;
  }

  private FileTransferProgress createFileTransferProgress(@NotNull DeviceFileEntry entry, @NotNull DownloadProgress progress) {
    return new FileTransferProgress() {
      @Override
      public void progress(long currentBytes, long totalBytes) {
        progress.onProgress(entry.getFullPath(), currentBytes, totalBytes);
      }

      @Override
      public boolean isCancelled() {
        return progress.isCancelled();
      }
    };
  }

  @NotNull
  private ListenableFuture<List<DeviceFileEntry>> mapPathsToEntries(@NotNull DeviceFileSystem fileSystem, @NotNull List<String> paths) {
    List<DeviceFileEntry> entries = new ArrayList<>();
    ListenableFuture<Void> allDone = myTaskExecutor.executeFuturesInSequence(paths.iterator(), path -> {
      ListenableFuture<DeviceFileEntry> futureEntry = fileSystem.getEntry(path);
      return myTaskExecutor.transform(futureEntry, entry -> { entries.add(entry); return null; });
    });

    return myTaskExecutor.transform(allDone, aVoid -> entries);
  }

  @NotNull
  private Path getDefaultDownloadPath() {
    return myDefaultDownloadPath.get();
  }

  @NotNull
  private static String mapName(String name) {
    return PathUtilRt.suggestFileName(name, /*allowDots*/true, /*allowSpaces*/true);
  }

  @Override
  @NotNull
  public Path getPathForEntry(@NotNull DeviceFileEntry file, @NotNull Path destinationPath) {
    List<String> entryPathComponents = new ArrayList<>();
    for (DeviceFileEntry entry = file; entry != null; entry = entry.getParent()) {
      entryPathComponents.add(mapName(entry.getName()));
    }
    Collections.reverse(entryPathComponents);

    Path entryDestinationPath = destinationPath;
    for (String name : entryPathComponents) {
      entryDestinationPath = entryDestinationPath.resolve(name);
    }

    return entryDestinationPath;
  }

  private static void deleteTemporaryFile(@NotNull Path localPath) {
    try {
      Files.deleteIfExists(localPath);
    }
    catch (IOException e) {
      LOGGER.warn(String.format("Error deleting device file from local file system \"%s\"", localPath), e);
    }
  }
}
