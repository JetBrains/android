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

import com.android.tools.idea.concurrent.FutureCallbackExecutor;
import com.android.tools.idea.device.fs.DeviceFileId;
import com.android.tools.idea.device.fs.DownloadProgress;
import com.android.tools.idea.device.fs.DownloadedFileData;
import com.android.tools.idea.deviceExplorer.FileHandler;
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.android.tools.idea.explorer.options.DeviceFileExplorerSettings;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;


/**
 * Abstraction over the application logic of the Device Explorer UI
 */
public class DeviceExplorerFileManagerImpl implements DeviceExplorerFileManager {
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerFileManagerImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final List<VirtualFile> myTemporaryEditorFiles = new ArrayList<>();
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

  @VisibleForTesting
  public DeviceExplorerFileManagerImpl(@NotNull Project project,
                                       @NotNull Executor edtExecutor,
                                       @NotNull Executor taskExecutor,
                                       @NotNull Supplier<Path> downloadPathSupplier) {
    myProject = project;
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerAdapter());
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
    Path relativePath = getEntryPath(entry);
    return devicePath.resolve(relativePath);
  }

  @NotNull
  @Override
  public ListenableFuture<DownloadedFileData> downloadFileEntry(@NotNull DeviceFileEntry entry,
                                                                @NotNull Path localPath,
                                                                @NotNull DownloadProgress progress) {
    SettableFuture<DownloadedFileData> futureResult = SettableFuture.create();

    FileUtils.mkdirs(localPath.getParent().toFile());
    ListenableFuture<VirtualFile> getVirtualFile = DeviceExplorerFilesUtils.findFile(localPath);

    myEdtExecutor.addCallback(getVirtualFile, new FutureCallback<VirtualFile>() {
      @Override
      public void onSuccess(VirtualFile virtualFile) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          deleteVirtualFile(futureResult, virtualFile);
          downloadFileAndAdditionalFiles(futureResult, entry, localPath, progress);
        });
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        downloadFileAndAdditionalFiles(futureResult, entry, localPath, progress);
      }
    });

    return futureResult;
  }

  private void deleteVirtualFile(@NotNull SettableFuture<DownloadedFileData> futureResult, @NotNull VirtualFile virtualFile) {
    // Using VFS to delete files has the advantage of throwing VFS events,
    // so listeners can react to actions on the files - for example by closing a file before it being deleted.
    try {
      virtualFile.delete(this);
    }
    catch (IOException e) {
      futureResult.setException(e);
    }
  }

  private void downloadFileAndAdditionalFiles(@NotNull SettableFuture<DownloadedFileData> futureResult,
                                              @NotNull DeviceFileEntry entry,
                                              @NotNull Path localPath,
                                              @NotNull DownloadProgress progress) {
    downloadFile(entry, localPath, progress, new FutureCallback<VirtualFile>() {
      @Override
      public void onSuccess(VirtualFile virtualFile) {
        ListenableFuture<List<VirtualFile>> downloadAdditionalFiles = downloadAdditionalFiles(virtualFile, entry, progress);
        myTaskExecutor.addCallback(downloadAdditionalFiles, new FutureCallback<List<VirtualFile>>() {
          @Override
          public void onSuccess(List<VirtualFile> additionalVirtualFiles) {
            futureResult.set(
              new DownloadedFileData(
                new DeviceFileId(entry.getFileSystem().getName(), entry.getFullPath()),
                virtualFile,
                additionalVirtualFiles)
            );
          }

          @Override
          public void onFailure(@NotNull Throwable throwable) {
            futureResult.setException(throwable);
          }
        });
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
      }
    });
  }

  /**
   * Downloads the file corresponding to the {@link DeviceFileEntry} passed as argument, to the local path specified.
   * @param entry The entry corresponding to the file to download.
   * @param localPath Where to download the file.
   * @param progress Progress indicator for the download operation.
   * @param callback An optional callback called once the download is over (success or failure).
   * @return
   */
  @NotNull
  private ListenableFuture<VirtualFile> downloadFile(@NotNull DeviceFileEntry entry,
                                                     @NotNull Path localPath,
                                                     @NotNull DownloadProgress progress,
                                                     @Nullable FutureCallback<VirtualFile> callback) {
    FileTransferProgress fileTransferProgress = createFileTransferProgress(entry, progress);
    progress.onStarting(Paths.get(entry.getFullPath()));
    ListenableFuture<Void> downloadFileFuture = entry.downloadFile(localPath, fileTransferProgress);
    ListenableFuture<VirtualFile> getVirtualFile = myTaskExecutor.transformAsync(downloadFileFuture,
                                                                                 aVoid -> DeviceExplorerFilesUtils.findFile(localPath));
    myEdtExecutor.addCallback(getVirtualFile, new FutureCallback<VirtualFile>() {
      @Override
      public void onSuccess(VirtualFile virtualFile) {
        progress.onCompleted(Paths.get(entry.getFullPath()));
        if (callback != null) callback.onSuccess(virtualFile);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        progress.onCompleted(Paths.get(entry.getFullPath()));
        deleteTemporaryFile(localPath);
        if (callback != null) callback.onFailure(t);
      }
    });

    return getVirtualFile;
  }

  /**
   * Downloads files in addition to the original file, if required.
   * <p>Some files require additional files to be downloaded in addition to the one selected by the user.
   * (eg. a .db file requires other two files to be downloaded with it, .db-shm and .db-wal)
   *
   * These files are downloaded in sequence because adb does not support parallel operations.</p>
   * @param originalLocalPath the local path of the original file.
   * @param originalEntry the entry corresponding to the original file.
   * @param progress used to keep track of the download progress.
   * @return a [Future] that completes when all additional files have been downloaded.
   */
  @NotNull
  private ListenableFuture<List<VirtualFile>> downloadAdditionalFiles(@NotNull VirtualFile originalFile,
                                                                      @NotNull DeviceFileEntry originalEntry,
                                                                      @NotNull DownloadProgress progress) {
    List<String> additionalPaths = getAdditionalDevicePaths(originalEntry, originalFile);

    ListenableFuture<List<DeviceFileEntry>> additionalEntriesFuture;
    // if the additional entries all have the same parent it is much faster to get them directly using the parent node.
    if (haveSameParent(originalEntry, additionalPaths)) {
      DeviceFileEntry parent = originalEntry.getParent();
      assert parent != null;
      additionalEntriesFuture = getEntriesFromCommonParent(parent, additionalPaths);
    } else {
      additionalEntriesFuture = mapPathsToEntries(originalEntry.getFileSystem(), additionalPaths);
    }

    List<VirtualFile> virtualFiles = new ArrayList<>();

    ListenableFuture<Void> fileDownloaded = myTaskExecutor.transformAsync(additionalEntriesFuture, additionalEntries -> {
      assert additionalEntries != null;
      return myEdtExecutor.executeFuturesInSequence(
        additionalEntries.iterator(),
        additionalEntry -> {
          Path localPath = getDefaultLocalPathForEntry(additionalEntry);
          FileUtils.mkdirs(localPath.getParent().toFile());

          ListenableFuture<VirtualFile> downloadCompleted =
            downloadFile(additionalEntry, localPath, progress, null);
          return myTaskExecutor.transform(downloadCompleted, virtualFile -> { virtualFiles.add(virtualFile); return null; });
        }
      );
    });

    return myTaskExecutor.transform(fileDownloaded, aVoid -> virtualFiles);
  }

  /**
   * Maps all the paths passed as argument to children of the given {@link DeviceFileEntry} node.
   */
  @NotNull
  private ListenableFuture<List<DeviceFileEntry>> getEntriesFromCommonParent(@NotNull DeviceFileEntry parent, @NotNull List<String> paths) {
    ListenableFuture<List<DeviceFileEntry>> getEntries = parent.getEntries();
    return myTaskExecutor.transform(getEntries, entries -> {
      assert entries != null;
      return paths
        .stream()
        .map(path -> {
          Optional<DeviceFileEntry> additionalEntryOptional = entries.stream().filter(e -> e.getFullPath().equals(path)).findFirst();
          return additionalEntryOptional.get();
        })
        .collect(Collectors.toList());
    });
  }

  private boolean haveSameParent(@NotNull DeviceFileEntry originalEntry, @NotNull List<String> additionalPaths) {
    DeviceFileEntry parent = originalEntry.getParent();
    String parentPath;
    if (parent != null)
      parentPath = parent.getFullPath();
    else
      parentPath = "";

    for (String currentPath : additionalPaths) {
      if (!parentPath.equals(AdbPathUtil.getParentPath(currentPath))) {
        return false;
      }
    }

    return true;
  }

  private FileTransferProgress createFileTransferProgress(@NotNull DeviceFileEntry entry, @NotNull DownloadProgress progress) {
    return new FileTransferProgress() {
      @Override
      public void progress(long currentBytes, long totalBytes) {
        progress.onProgress(Paths.get(entry.getFullPath()), currentBytes, totalBytes);
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
  private List<String> getAdditionalDevicePaths(@NotNull DeviceFileEntry entry, @NotNull VirtualFile virtualFile) {
    List<String> additionalPaths = new ArrayList<>();
    for(FileHandler fileHandler : FileHandler.EP_NAME.getExtensions()) {
      additionalPaths.addAll(fileHandler.getAdditionalDevicePaths(entry.getFullPath(), virtualFile));
    }
    return additionalPaths;
  }

  @Override
  @NotNull
  public ListenableFuture<Void> openFile(@NotNull DeviceFileEntry entry, @NotNull Path localPath) {
    return openFileInEditorWorker(entry, localPath);
  }

  @NotNull
  private Path getDefaultDownloadPath() {
    return myDefaultDownloadPath.get();
  }

  @NotNull
  private static Path mapName(String name) {
    return Paths.get(PathUtilRt.suggestFileName(name, /*allowDots*/true, /*allowSpaces*/true));
  }

  @NotNull
  private static Path getEntryPath(@NotNull DeviceFileEntry file) {
    List<Path> names = new ArrayList<>();
    for (DeviceFileEntry entry = file; entry != null; entry = entry.getParent()) {
      names.add(mapName(entry.getName()));
    }
    Collections.reverse(names);

    Optional<Path> path = names.stream().reduce(Path::resolve);
    assert path.isPresent();
    return path.get();
  }

  private ListenableFuture<Void> openFileInEditorWorker(@NotNull DeviceFileEntry entry, @NotNull Path localPath) {
    ListenableFuture<VirtualFile> futureFile = DeviceExplorerFilesUtils.findFile(localPath);

    return myEdtExecutor.transform(futureFile, file -> {
      // Set the device/path information on the virtual file so custom editors
      // (e.g. database viewer) know which device this file is coming from.
      DeviceFileId fileInfo = new DeviceFileId(entry.getFileSystem().getName(), entry.getFullPath());
      file.putUserData(DeviceFileId.KEY, fileInfo);

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file, myProject);
      if (type == null) {
        throw new CancellationException("Operation cancelled by user");
      }

      OpenFileAction.openFile(file, myProject);

      myTemporaryEditorFiles.add(file);
      return null;
    });
  }

  private static void deleteTemporaryFile(@NotNull Path localPath) {
    try {
      Files.deleteIfExists(localPath);
    }
    catch (IOException e) {
      LOGGER.warn(String.format("Error deleting device file from local file system \"%s\"", localPath), e);
    }
  }

  private class MyFileEditorManagerAdapter implements FileEditorManagerListener {
    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (myTemporaryEditorFiles.contains(file)) {
        myTemporaryEditorFiles.remove(file);

        Path localPath = Paths.get(file.getPath());
        deleteTemporaryFile(localPath);
      }
    }
  }
}
