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
package com.android.tools.idea.explorer.mocks;

import com.android.tools.idea.explorer.DeviceExplorerFileManager;
import com.android.tools.idea.explorer.DeviceExplorerFileManagerImpl;
import com.android.tools.idea.explorer.FutureCallbackExecutor;
import com.android.tools.idea.explorer.FutureValuesTracker;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

public class MockDeviceExplorerFileManager implements DeviceExplorerFileManager, Disposable {
  private static final Logger LOGGER = Logger.getInstance(MockDeviceExplorerFileManager.class);

  @NotNull private final DeviceExplorerFileManagerImpl myFileManagerImpl;
  @NotNull private final Project myProject;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final Set<DeviceFileSystem> myDevices = new HashSet<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntry> myDownloadFileEntryTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntry> myDownloadFileEntryCompletionTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Path> myOpenFileInEditorTracker = new FutureValuesTracker<>();
  @Nullable private RuntimeException myOpenFileInEditorError;

  public MockDeviceExplorerFileManager(@NotNull Project project, @NotNull Executor edtExecutor) {
    myProject = project;
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
    myFileManagerImpl = new DeviceExplorerFileManagerImpl(project, edtExecutor);
  }

  @TestOnly
  public void setDefaultDownloadPath(@NotNull Path path) {
    myFileManagerImpl.setDefaultDownloadPath(path);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFileEntry(@NotNull DeviceFileEntry entry, @NotNull Path localPath, @NotNull FileTransferProgress progress) {
    myDownloadFileEntryTracker.produce(entry);

    myDevices.add(entry.getFileSystem());

    ListenableFuture<Void> futureResult = myFileManagerImpl.downloadFileEntry(entry, localPath, progress);
    myEdtExecutor.addCallback(futureResult, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        myDownloadFileEntryCompletionTracker.produce(entry);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myDownloadFileEntryCompletionTracker.produceException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  @Override
  public Path getDefaultLocalPathForEntry(@NotNull DeviceFileEntry entry) {
    return myFileManagerImpl.getDefaultLocalPathForEntry(entry);
  }

  @Override
  @NotNull
  public ListenableFuture<Void> openFileInEditor(@NotNull Path localPath, boolean focusEditor) {
    myOpenFileInEditorTracker.produce(localPath);
    if (myOpenFileInEditorError != null) {
      return Futures.immediateFailedFuture(myOpenFileInEditorError);
    }
    return myFileManagerImpl.openFileInEditor(localPath, focusEditor);
  }

  @Override
  public void dispose() {
    // Close all editors
    FileEditorManager manager = FileEditorManager.getInstance(myProject);
    Arrays.stream(manager.getOpenFiles()).forEach((file) -> {
      manager.closeFile(file);

      // The TestFileEditorManager does not publish events to the message bus,
      // so we do it here to ensure we hit the code in our DeviceExplorerFileManagerImpl class.
      myProject.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(manager, file);
    });

    // Delete local directories associated to test devices
    myDevices.forEach(fileSystem -> {
      Path path = myFileManagerImpl.getDefaultLocalPathForDevice(fileSystem);
      try {
        PathKt.delete(path);
      }
      catch (Throwable t) {
        LOGGER.warn(String.format("Error deleting local path \"%s\"", path), t);
      }
    });
    myDevices.clear();
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntry> getDownloadFileEntryTracker() {
    return myDownloadFileEntryTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntry> getDownloadFileEntryCompletionTracker() {
    return myDownloadFileEntryCompletionTracker;
  }

  @NotNull
  public FutureValuesTracker<Path> getOpenFileInEditorTracker() {
    return myOpenFileInEditorTracker;
  }

  public void setOpenFileInEditorError(@Nullable RuntimeException e) {
    myOpenFileInEditorError = e;
  }
}
