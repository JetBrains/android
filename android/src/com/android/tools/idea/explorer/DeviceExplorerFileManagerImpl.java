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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Abstraction over the application logic of the Device Explorer UI
 */
public class DeviceExplorerFileManagerImpl implements DeviceExplorerFileManager {
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerFileManagerImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final List<VirtualFile> myTemporaryEditorFiles = new ArrayList<>();
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private Supplier<Path> myDefaultDownloadPath;

  public DeviceExplorerFileManagerImpl(@NotNull Project project, @NotNull Executor edtExecutor) {
    this(project, edtExecutor,
         () -> Paths.get(DeviceFileExplorerSettings.getInstance().getDownloadLocation()));
  }

  @VisibleForTesting
  public DeviceExplorerFileManagerImpl(@NotNull Project project, @NotNull Executor edtExecutor,
                                       @NotNull Supplier<Path> downloadPathSupplier) {
    myProject = project;
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerAdapter());
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
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
  public ListenableFuture<Void> downloadFileEntry(@NotNull DeviceFileEntry entry, @NotNull Path localPath, @NotNull FileTransferProgress progress) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    FileUtils.mkdirs(localPath.getParent().toFile());
    ListenableFuture<VirtualFile> getVirtualFile = DeviceExplorerFilesUtils.findFile(localPath);

    // Using VFS to delete files has the advantage of throwing VFS events,
    // so listeners can react to actions on the files - for example by closing a file before it being deleted.
    myEdtExecutor.addCallback(getVirtualFile, new FutureCallback<VirtualFile>() {
      @Override
      public void onSuccess(VirtualFile virtualFile) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          deleteVirtualFile(futureResult, virtualFile);
          downloadFile(futureResult, entry, localPath, progress);
        });
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        downloadFile(futureResult, entry, localPath, progress);
      }
    });

    return futureResult;
  }

  private void deleteVirtualFile(SettableFuture<Void> futureResult, VirtualFile virtualFile) {
    if (virtualFile != null) {
      try {
        virtualFile.delete(this);
      }
      catch (IOException e) {
        futureResult.setException(e);
      }
    }
  }

  private void downloadFile(
    SettableFuture<Void> futureResult,
    @NotNull DeviceFileEntry entry,
    @NotNull Path localPath,
    @NotNull FileTransferProgress progress
  ) {
    ListenableFuture<Void> result = entry.downloadFile(localPath, progress);
    myEdtExecutor.addCallback(result, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        futureResult.set(null);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
        deleteTemporaryFile(localPath);
      }
    });
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
