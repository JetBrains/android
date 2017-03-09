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

import com.android.tools.idea.util.FutureUtils;
import com.android.tools.idea.explorer.adbimpl.AdbShellCommandException;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceState;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("SameParameterValue")
public class MockDeviceFileSystem implements DeviceFileSystem {
  @NotNull private final MockDeviceFileSystemService myService;
  @NotNull private final String myName;
  @NotNull private final MockDeviceFileEntry myRoot;
  private long myDownloadChunkSize = 1024;
  private long myUploadChunkSize = 1024;
  private int myDownloadFileChunkIntervalMillis = MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS;
  private int myUploadFileChunkIntervalMillis = MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS;
  private Throwable myDownloadError;
  private Throwable myRootDirectoryError;
  private Throwable myUploadError;

  public MockDeviceFileSystem(@NotNull MockDeviceFileSystemService service, @NotNull String name) {
    myService = service;
    myName = name;
    myRoot = MockDeviceFileEntry.createRoot(this);
  }

  @NotNull
  public MockDeviceFileSystemService getService() {
    return myService;
  }

  @NotNull
  public MockDeviceFileEntry getRoot() {
    return myRoot;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public DeviceState getDeviceState() {
    return DeviceState.ONLINE;
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getRootDirectory() {
    if (myRootDirectoryError != null) {
      return FutureUtils.delayedError(myRootDirectoryError, MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS);
    }
    return FutureUtils.delayedValue(myRoot, MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS);
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getEntry(@NotNull String path) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public ListenableFuture<Void> downloadFile(@NotNull DeviceFileEntry entry,
                                             @NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    if (myDownloadError != null) {
      return FutureUtils.delayedError(myDownloadError, MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS);
    }
    return new DownloadWorker((MockDeviceFileEntry)entry, localPath, progress).myFutureResult;
  }

  @NotNull
  public ListenableFuture<Void> uploadFile(@NotNull Path localFilePath,
                                           @NotNull DeviceFileEntry remoteDirectory,
                                           @NotNull String fileName,
                                           @NotNull FileTransferProgress progress) {
    if (myUploadError != null) {
      return FutureUtils.delayedError(myUploadError, MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS);
    }

    return new UploadWorker((MockDeviceFileEntry)remoteDirectory, fileName, localFilePath, progress).myFutureResult;
  }

  public void setDownloadFileChunkSize(long size) {
    myDownloadChunkSize = size;
  }

  public void setDownloadFileChunkIntervalMillis(int millis) {
    myDownloadFileChunkIntervalMillis = millis;
  }

  public void setUploadFileChunkSize(long size) {
    myUploadChunkSize = size;
  }

  public void setUploadFileChunkIntervalMillis(int millis) {
    myUploadFileChunkIntervalMillis = millis;
  }

  public void setDownloadError(@Nullable Throwable t) {
    myDownloadError = t;
  }

  public void setRootDirectoryError(@Nullable Throwable t) {
    myRootDirectoryError = t;
  }

  public void setUploadError(@Nullable Throwable t) {
    myUploadError = t;
  }

  public class DownloadWorker implements Disposable {
    @NotNull private final MockDeviceFileEntry myEntry;
    @NotNull private final Path myPath;
    @NotNull private final FileTransferProgress myProgress;
    @NotNull private final SettableFuture<Void> myFutureResult;
    @NotNull private final Alarm myAlarm;
    private long myCurrentOffset;
    @Nullable private FileOutputStream myOutputStream;

    public DownloadWorker(@NotNull MockDeviceFileEntry entry, @NotNull Path path, @NotNull FileTransferProgress progress) {
      myEntry = entry;
      myPath = path;
      myProgress = progress;
      myFutureResult = SettableFuture.create();
      myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
      Disposer.register(ApplicationManager.getApplication(), this);
      addRequest();
    }

    private void addRequest() {
      myAlarm.addRequest(this::processNextChunk, myDownloadFileChunkIntervalMillis);
    }

    public void processNextChunk() {
      assert !myFutureResult.isDone();

      // Create file if needed
      try {
        writeBytes(0);
      }
      catch (IOException e) {
        doneWithError(e);
        return;
      }

      // Report progress
      final long currentOffset = myCurrentOffset;
      myService.getEdtExecutor().execute(() -> myProgress.progress(currentOffset, myEntry.getSize()));

      // Write bytes and enqueue next request if not done yet
      if (myCurrentOffset < myEntry.getSize()) {
        addRequest();

        long chunkSize = Math.min(myDownloadChunkSize, myEntry.getSize() - myCurrentOffset);
        try {
          writeBytes(chunkSize);
          myCurrentOffset += chunkSize;
        }
        catch (IOException e) {
          doneWithError(e);
        }
        return;
      }

      // Complete future if done
      done();
    }

    private void done() {
      try {
        Disposer.dispose(this);
      }
      finally {
        myFutureResult.set(null);
      }
    }

    private void doneWithError(Throwable t) {
      try {
        Disposer.dispose(this);
      }
      finally {
        myFutureResult.setException(t);
      }
    }

    private void writeBytes(long count) throws IOException {
      if (myOutputStream == null) {
        myOutputStream = new FileOutputStream(myPath.toFile());
      }
      if (count > 0) {
        byte[] bytes = new byte[(int)count];
        // Write ascii characters to that the file is easily auto-detected as a text file
        // in unit tests.
        for (int i = 0; i < count; i++) {
          bytes[i] = (byte)((i % 80 == 0) ? '\n' : ('0' + (i % 10)));
        }
        myOutputStream.write(bytes);
      }
    }

    @Override
    public void dispose() {
      myAlarm.cancelAllRequests();
      if (myOutputStream != null) {
        try {
          myOutputStream.close();
          myOutputStream = null;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  public class UploadWorker implements Disposable {
    @NotNull private final MockDeviceFileEntry myEntry;
    @NotNull private final String myFileName;
    @NotNull private final Path myPath;
    @NotNull private final FileTransferProgress myProgress;
    @NotNull private final SettableFuture<Void> myFutureResult;
    @NotNull private final Alarm myAlarm;
    private long myCurrentOffset;
    private long myFileLength;
    private MockDeviceFileEntry myCreatedEntry;

    public UploadWorker(@NotNull MockDeviceFileEntry entry, @NotNull String fileName, @NotNull Path path, @NotNull FileTransferProgress progress) {
      myEntry = entry;
      myFileName = fileName;
      myPath = path;
      myProgress = progress;
      myFutureResult = SettableFuture.create();
      myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
      Disposer.register(ApplicationManager.getApplication(), this);
      addRequest();
    }

    private void addRequest() {
      myAlarm.addRequest(this::processNextChunk, myUploadFileChunkIntervalMillis);
    }

    public void processNextChunk() {
      assert !myFutureResult.isDone();

      // Add entry right away (simulate behavior of device upload, where an empty file is immediately created on upload)
      if (myCreatedEntry == null) {
        try {
          myFileLength = Files.size(myPath);
          myCreatedEntry = myEntry.addFile(myFileName);
        }
        catch (AdbShellCommandException | IOException e) {
          doneWithError(e);
          return;
        }
      }

      // Report progress
      final long currentOffset = myCurrentOffset;
      myService.getEdtExecutor().execute(() -> myProgress.progress(currentOffset, myFileLength));

      // Write bytes and enqueue next request if not done yet
      if (myCurrentOffset < myFileLength) {
        addRequest();

        long chunkSize = Math.min(myUploadChunkSize, myFileLength - myCurrentOffset);
        myCreatedEntry.setSize(myCreatedEntry.getSize() + chunkSize);
        myCurrentOffset += chunkSize;
        return;
      }

      // Complete future if done
      done();
    }

    private void done() {
      try {
        Disposer.dispose(this);
      }
      finally {
        myFutureResult.set(null);
      }
    }

    private void doneWithError(Throwable t) {
      try {
        Disposer.dispose(this);
      }
      finally {
        myFutureResult.setException(t);
      }
    }

    @Override
    public void dispose() {
      myAlarm.cancelAllRequests();
    }
  }
}
