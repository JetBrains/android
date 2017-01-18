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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @Nullable private Path myAppDataPath;

  public DeviceExplorerFileManagerImpl(@NotNull Project project, @NotNull Executor edtExecutor) {
    myProject = project;
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerAdapter());
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
  }

  @NotNull
  public Path getLocalPathForDevice(@NotNull DeviceFileSystem device) {
    Path rootPath = getAppDataPath();
    return rootPath.resolve(mapName(device.getName()));
  }

  /**
   * Download asynchronously the content of a {@link DeviceFileEntry} onto the local file system.
   * and returns a {@link ListenableFuture} the contains the local {@link Path} of the downloaded
   * file once the download is completed. The <code>progress</code> callback is regularly notified
   * of the current progress of the download operation.
   */
  @NotNull
  @Override
  public ListenableFuture<Path> downloadFileEntry(@NotNull DeviceFileEntry entry, @NotNull FileTransferProgress progress) {
    SettableFuture<Path> futureResult = SettableFuture.create();

    Path localPath;
    try {
      Path devicePath = getLocalPathForDevice(entry.getFileSystem());
      Path relativePath = getEntryPath(entry);
      localPath = devicePath.resolve(relativePath);

      // Ensure parent directories are created and file is not present
      FileUtils.mkdirs(localPath.getParent().toFile());
      FileUtils.deleteIfExists(localPath.toFile());
    }
    catch (IOException e) {
      futureResult.setException(e);
      return futureResult;
    }

    // Download the local file
    ListenableFuture<Void> result = entry.getFileSystem().downloadFile(entry, localPath, progress);
    myEdtExecutor.addCallback(result, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        futureResult.set(localPath);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
        deleteTemporaryFile(localPath);
      }
    });

    return futureResult;
  }

  /**
   * Open a downloaded file in an editor window.
   */
  @Override
  public void openFileInEditor(@NotNull Path localPath, boolean focusEditor) {
    try {
      openFileInEditorWorker(localPath, focusEditor);
    } catch(Throwable t){
      deleteTemporaryFile(localPath);
      throw t;
    }
  }

  @NotNull
  private Path getAppDataPath() {
    if (myAppDataPath == null) {
      myAppDataPath = getAppDataPathWorker();
    }
    return myAppDataPath;
  }

  @NotNull
  private static Path getAppDataPathWorker() {
    String userHome = System.getProperty("user.home");
    String path = null;
    if (SystemInfo.isWindows) {
      path = System.getenv("LOCALAPPDATA");
      if (StringUtil.isEmpty(path)) {
        path = System.getenv("APPDATA");
      }
    }
    else if (SystemInfo.isMac) {
      path = FileUtil.join(userHome, "Library");
    }
    else if (SystemInfo.isLinux) {
      path = FileUtil.join(userHome, ".local", "share");
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

  private void openFileInEditorWorker(@NotNull Path localPath, boolean focusEditor) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(localPath.toString());
    if (file == null) {
      throw new RuntimeException(String.format("Unable to locate file \"%s\"", localPath));
    }

    FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file, myProject);
    if (type == null) {
      throw new CancellationException("Operation cancelled by user");
    }

    FileEditor[] editors = FileEditorManager.getInstance(myProject).openFile(file, focusEditor);
    if (editors.length == 0) {
      throw new RuntimeException(String.format("Unable to open file \"%s\" in editor", localPath));
    }
    myTemporaryEditorFiles.add(file);
  }

  public void deleteTemporaryFile(@NotNull Path localPath) {
    try {
      Files.deleteIfExists(localPath);
    }
    catch (IOException e) {
      LOGGER.warn(String.format("Error deleting device file from local file system \"%s\"", localPath), e);
    }
  }

  private class MyFileEditorManagerAdapter extends FileEditorManagerAdapter {
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
