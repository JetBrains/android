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
import com.android.tools.idea.concurrency.FutureCallbackExecutor;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceState;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdbDeviceFileSystem implements DeviceFileSystem {
  @NotNull private final IDevice myDevice;
  @NotNull private final AdbDeviceCapabilities myDeviceCapabilities;
  @NotNull private final AdbFileListing myFileListing;
  @NotNull private final AdbFileOperations myFileOperations;
  @NotNull private final AdbFileTransfer myFileTransfer;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final FutureCallbackExecutor myTaskExecutor;

  public AdbDeviceFileSystem(@NotNull IDevice device, @NotNull Executor edtExecutor, @NotNull Executor taskExecutor) {
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
    myTaskExecutor = new FutureCallbackExecutor(taskExecutor);
    myDevice = device;
    myDeviceCapabilities = new AdbDeviceCapabilities(myDevice);
    myFileListing = new AdbFileListing(myDevice, myDeviceCapabilities, myTaskExecutor);
    myFileOperations = new AdbFileOperations(myDevice, myDeviceCapabilities, myTaskExecutor);
    myFileTransfer = new AdbFileTransfer(myDevice, myFileOperations, myEdtExecutor, myTaskExecutor);
  }

  public AdbDeviceFileSystem(AdbDeviceFileSystemService service, IDevice device) {
    this(device, service.getEdtExecutor(), service.getTaskExecutor());
  }

  boolean isDevice(@Nullable IDevice device) {
    return myDevice.equals(device);
  }

  @NotNull
  IDevice getDevice() {
    return myDevice;
  }

  @NotNull
  public AdbDeviceCapabilities getCapabilities() {
    return myDeviceCapabilities;
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
  public AdbFileTransfer getAdbFileTransfer() {
    return myFileTransfer;
  }

  @NotNull
  FutureCallbackExecutor getTaskExecutor() {
    return myTaskExecutor;
  }

  @NotNull
  @Override
  public String getName() {
    return myDevice.getName();
  }

  @NotNull
  @Override
  public String getDeviceSerialNumber() {
    return myDevice.getSerialNumber();
  }

  @NotNull
  @Override
  public DeviceState getDeviceState() {
    IDevice.DeviceState state = myDevice.getState();

    if (state == null) {
      return DeviceState.DISCONNECTED;
    }

    switch (state) {
      case ONLINE:
        return DeviceState.ONLINE;
      case OFFLINE:
        return DeviceState.OFFLINE;
      case UNAUTHORIZED:
        return DeviceState.UNAUTHORIZED;
      case DISCONNECTED:
        return DeviceState.DISCONNECTED;
      case BOOTLOADER:
        return DeviceState.BOOTLOADER;
      case RECOVERY:
        return DeviceState.RECOVERY;
      case SIDELOAD:
        return DeviceState.SIDELOAD;
      default:
        return DeviceState.DISCONNECTED;
    }
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getRootDirectory() {
    return getTaskExecutor().transform(getAdbFileListing().getRoot(), entry -> {
      assert entry != null;
      return new AdbDeviceDefaultFileEntry(this, entry, null);
    });
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

        String[] pathSegments = path.substring(1).split(FileListingService.FILE_SEPARATOR);
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
  public ListenableFuture<AdbDeviceFileEntry> resolveMountPoint(@NotNull AdbDeviceFileEntry entry) {
    return getTaskExecutor().executeAsync(() -> {
      // Root devices or "su 0" devices don't need mount points
      if (myDeviceCapabilities.supportsSuRootCommand() || myDeviceCapabilities.isRoot()) {
        return createDirectFileEntry(entry);
      }

      // The "/data" folder has directories where we need to use "run-as"
      if (Objects.equals(entry.getFullPath(), "/data")) {
        return new AdbDeviceDataDirectoryEntry(entry);
      }

      // Default behavior
      return createDirectFileEntry(entry);
    });
  }

  @NotNull
  private static AdbDeviceDirectFileEntry createDirectFileEntry(@NotNull AdbDeviceFileEntry entry) {
    return new AdbDeviceDirectFileEntry(entry.myDevice, entry.myEntry, entry.myParent, null);
  }
}
