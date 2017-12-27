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

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.android.utils.FileUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.filechooser.FileSystemView;
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


/**
 * Abstraction over the application logic of the Device Explorer UI
 */
public class DeviceExplorerFileManagerImpl implements DeviceExplorerFileManager {
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerFileManagerImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final List<VirtualFile> myTemporaryEditorFiles = new ArrayList<>();
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @Nullable private Path myDefaultDownloadPath;

  public DeviceExplorerFileManagerImpl(@NotNull Project project, @NotNull Executor edtExecutor) {
    myProject = project;
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerAdapter());
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
  }

  @TestOnly
  public void setDefaultDownloadPath(@NotNull Path path) {
    myDefaultDownloadPath = path;
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

    try {
      // Ensure parent directories are created and file is not present
      FileUtils.mkdirs(localPath.getParent().toFile());
      FileUtils.deleteIfExists(localPath.toFile());
    }
    catch (IOException e) {
      futureResult.setException(e);
      return futureResult;
    }

    // Download the local file
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

    return futureResult;
  }

  @Override
  @NotNull
  public ListenableFuture<Void> openFileInEditor(@NotNull Path localPath, boolean focusEditor) {
    return openFileInEditorWorker(localPath, focusEditor);
  }

  @NotNull
  private Path getDefaultDownloadPath() {
    if (myDefaultDownloadPath == null) {
      myDefaultDownloadPath = getDefaultDownloadPathWorker();
    }
    return myDefaultDownloadPath;
  }

  @NotNull
  private static Path getDefaultDownloadPathWorker() {
    String userHome = System.getProperty("user.home");
    String path = null;
    if (SystemInfo.isWindows) {
      // On Windows, we need the localized "Documents" folder name
      path = FileSystemView.getFileSystemView().getDefaultDirectory().getPath();
    }
    else if (SystemInfo.isMac) {
      // On OSX, "Documents" is not localized
      path = FileUtil.join(userHome, "Documents");
    }
    else if (SystemInfo.isLinux) {
      // On Linux, there is no standard "Documents" folder, so use the home folder
      path = userHome;
    }
    if (StringUtil.isEmpty(path)) {
      throw new RuntimeException("Platform is not supported");
    }
    path = FileUtil.join(path, "AndroidStudio", "DeviceExplorer");
    return Paths.get(path);
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

  private ListenableFuture<Void> openFileInEditorWorker(@NotNull Path localPath, boolean focusEditor) {
    // Note: We run this operation inside a transaction because we need to refresh a VirtualFile instance.
    //       See https://github.com/JetBrains/intellij-community/commit/10c0c11281b875e64c31186eac20fc28ba3fc37a
    SettableFuture<VirtualFile> futureFile = SettableFuture.create();
    TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
      VirtualFile localFile = VfsUtil.findFileByIoFile(localPath.toFile(), true);
      if (localFile == null) {
        futureFile.setException(new RuntimeException(String.format("Unable to locate file \"%s\"", localPath)));
      }
      else {
        futureFile.set(localFile);
      }
    });

    return myEdtExecutor.transform(futureFile, file -> {
      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file, myProject);
      if (type == null) {
        throw new CancellationException("Operation cancelled by user");
      }

      FileEditor[] editors = FileEditorManager.getInstance(myProject).openFile(file, focusEditor);
      if (editors.length == 0) {
        throw new RuntimeException(String.format("Unable to open file \"%s\" in editor", localPath));
      }
      myTemporaryEditorFiles.add(file);
      return null;
    });
  }

  public void deleteTemporaryFile(@NotNull Path localPath) {
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
