/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.tools.idea.explorer.FutureCallbackExecutor;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

public class AdbDeviceFileSystem implements DeviceFileSystem {
  @NotNull private final AdbDeviceFileSystemService myService;
  @NotNull private final IDevice myDevice;
  @NotNull private final AdbFileListing myFileListing;
  @NotNull private final AdbFileOperations myFileOperations;

  public AdbDeviceFileSystem(@NotNull AdbDeviceFileSystemService service, @NotNull IDevice device) {
    myService = service;
    myDevice = device;
    myFileListing = new AdbFileListing(myDevice, service.getTaskExecutor());
    myFileOperations = new AdbFileOperations(myDevice, service.getTaskExecutor());
  }

  boolean isDevice(@Nullable IDevice device) {
    return myDevice.equals(device);
  }

  @NotNull
  IDevice getDevice() {
    return myDevice;
  }

  @NotNull
  public AdbFileListing getAdbFileListing() {
    return myFileListing;
  }

  @NotNull
  public AdbFileOperations getAdbFileOperations() {
    return myFileOperations;
  }

  @NotNull
  FutureCallbackExecutor getEdtExecutor() {
    return myService.getEdtExecutor();
  }

  @NotNull
  FutureCallbackExecutor getTaskExecutor() {
    return myService.getTaskExecutor();
  }

  @NotNull
  @Override
  public String getName() {
    return myDevice.getName();
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getRootDirectory() {
    SettableFuture<DeviceFileEntry> futureResult = SettableFuture.create();

    getTaskExecutor().addCallback(getAdbFileListing().getRoot(), new FutureCallback<AdbFileListingEntry>() {
      @Override
      public void onSuccess(@Nullable AdbFileListingEntry result) {
        assert result != null;
        futureResult.set(new AdbDeviceFileEntry(AdbDeviceFileSystem.this, result, null));
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getEntry(@NotNull String path) {
    SettableFuture<DeviceFileEntry> resultFuture = SettableFuture.create();

    ListenableFuture<DeviceFileEntry> currentDir = getRootDirectory();
    getTaskExecutor().addCallback(currentDir, new FutureCallback<DeviceFileEntry>() {
      @Override
      public void onSuccess(@Nullable DeviceFileEntry result) {
        assert result != null;

        if (StringUtil.isEmpty(path) || StringUtil.equals(path, FileListingService.FILE_SEPARATOR)) {
          resultFuture.set(result);
          return;
        }

        String[] pathSegments = path.split(FileListingService.FILE_SEPARATOR);
        resolvePathSegments(resultFuture, result, pathSegments, 0);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        resultFuture.setException(t);
      }
    });

    return resultFuture;
  }

  private void resolvePathSegments(@NotNull SettableFuture<DeviceFileEntry> future,
                                   @NotNull DeviceFileEntry currentEntry,
                                   @NotNull String[] segments,
                                   int segmentIndex) {
    if (segmentIndex >= segments.length) {
      future.set(currentEntry);
      return;
    }

    if (!currentEntry.isDirectory()) {
      future.setException(new IllegalArgumentException("Segment is not a directory"));
      return;
    }

    ListenableFuture<List<DeviceFileEntry>> entriesFuture = currentEntry.getEntries();
    getTaskExecutor().addCallback(entriesFuture, new FutureCallback<List<DeviceFileEntry>>() {
      @Override
      public void onSuccess(@Nullable List<DeviceFileEntry> result) {
        assert result != null;

        Optional<DeviceFileEntry> entry = result
          .stream()
          .filter(x -> x.getName().equals(segments[segmentIndex]))
          .findFirst();
        if (!entry.isPresent()) {
          future.setException(new IllegalArgumentException("Path not found"));
        }
        else {
          resolvePathSegments(future, entry.get(), segments, segmentIndex + 1);
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        future.setException(t);
      }
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull DeviceFileEntry entry,
                                             @NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    if (!(entry instanceof AdbDeviceFileEntry)) {
      return Futures.immediateFailedFuture(new IllegalArgumentException("Invalid file entry"));
    }
    AdbDeviceFileEntry adbEntry = (AdbDeviceFileEntry)entry;
    return downloadFileWorker(adbEntry, localPath, progress);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull DeviceFileEntry parentEntry, @NotNull String fileName) {
    return myFileOperations.createNewFile(parentEntry.getFullPath(), fileName);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull DeviceFileEntry parentEntry, @NotNull String directoryName) {
    return myFileOperations.createNewDirectory(parentEntry.getFullPath(), directoryName);
  }

  @NotNull
  private ListenableFuture<Void> downloadFileWorker(@NotNull AdbDeviceFileEntry entry,
                                                    @NotNull Path localPath,
                                                    @NotNull FileTransferProgress progress) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    ListenableFuture<SyncService> futureSyncService = getSyncService();
    getTaskExecutor().addCallback(futureSyncService, new FutureCallback<SyncService>() {
      @Override
      public void onSuccess(@Nullable SyncService syncService) {
        assert syncService != null;

        try {
          long size = entry.getSize();
          syncService.pullFile(entry.myEntry.getFullPath(),
                               localPath.toString(),
                               new SingleFileSyncProgressMonitor(getEdtExecutor(), progress, size));
          futureResult.set(null);
        }
        catch (SyncException e) {
          if (e.wasCanceled()) {
            futureResult.setException(new CancellationException());
          }
          else {
            futureResult.setException(e);
          }
        }
        catch (Throwable t) {
          futureResult.setException(t);
        }
        finally {
          syncService.close();
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  private ListenableFuture<SyncService> getSyncService() {
    SettableFuture<SyncService> futureResult = SettableFuture.create();
    getTaskExecutor().execute(() -> {
      try {
        SyncService sync = myDevice.getSyncService();
        if (sync == null) {
          futureResult.setException(new IOException("Unable to open synchronization service to device"));
          return;
        }

        futureResult.set(sync);
      }
      catch (Throwable t) {
        futureResult.setException(new IOException("Unable to open synchronization service to device", t));
      }
    });
    return futureResult;
  }

  private static class SingleFileSyncProgressMonitor implements SyncService.ISyncProgressMonitor {
    private final Executor myCallbackExecutor;
    private final FileTransferProgress myProgress;
    private final long myTotalBytes;
    private long myCurrentBytes;

    public SingleFileSyncProgressMonitor(Executor callbackExecutor, FileTransferProgress progress, long totalBytes) {
      myCallbackExecutor = callbackExecutor;
      myProgress = progress;
      myTotalBytes = totalBytes;
    }

    @Override
    public void start(int totalWork) {
      // Note: The current implementation of SyncService always sends
      // "0" as the value of totalWork.
      myCallbackExecutor.execute(() -> myProgress.progress(0, myTotalBytes));
    }

    @Override
    public void stop() {
      myCallbackExecutor.execute(() -> myProgress.progress(myTotalBytes, myTotalBytes));
    }

    @Override
    public boolean isCanceled() {
      return myProgress.isCancelled();
    }

    @Override
    public void startSubTask(String name) {
      assert false : "A single file sync should not have multiple tasks";
    }

    @Override
    public void advance(int work) {
      myCurrentBytes += work;
      myCallbackExecutor.execute(() -> myProgress.progress(myCurrentBytes, myTotalBytes));
    }
  }
}