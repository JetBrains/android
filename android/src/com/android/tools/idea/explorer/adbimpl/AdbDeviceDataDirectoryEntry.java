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
package com.android.tools.idea.explorer.adbimpl;

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A custom {@link AdbDeviceFileEntry} implementation for the the "/data" directory of a device.
 *
 * <p>The purpose is to allow file operations on files under "/data/data/packageName" using the
 * "run-as" command shell prefix.
 */
public class AdbDeviceDataDirectoryEntry extends AdbDeviceForwardingFileEntry {
  @NotNull private final AdbDeviceDirectFileEntry myDirectEntry;

  public AdbDeviceDataDirectoryEntry(@NotNull AdbDeviceFileEntry entry) {
    super(entry.myDevice, entry.myEntry, entry.myParent);
    myDirectEntry = new AdbDeviceDirectFileEntry(entry.myDevice, entry.myEntry, entry.myParent, null);
  }

  @NotNull
  @Override
  public AdbDeviceFileEntry getForwardedFileEntry() {
    return myDirectEntry;
  }

  private static AdbFileListingEntry createDirectoryEntry(@NotNull AdbFileListingEntry parent, @NotNull String name) {
    return new AdbFileListingEntryBuilder(parent)
      .setPath(AdbPathUtil.resolve(parent.getFullPath(), name))
      .build();
  }

  private static AdbFileListingEntry createFileEntry(@NotNull AdbFileListingEntry parent, @NotNull String name) {
    return new AdbFileListingEntryBuilder(parent)
      .setPath(AdbPathUtil.resolve(parent.getFullPath(), name))
      .setKind(AdbFileListingEntry.EntryKind.FILE)
      .setSize(-1)
      .build();
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    return myDevice.getTaskExecutor().executeAsync(() -> {
      List<DeviceFileEntry> entries = new ArrayList<>();
      entries.add(new AdbDeviceDataAppDirectoryEntry(this, createDirectoryEntry(myEntry, "app")));
      entries.add(new AdbDeviceDataDataDirectoryEntry(this, createDirectoryEntry(myEntry, "data")));
      entries.add(new AdbDeviceDataLocalDirectoryEntry(this, createDirectoryEntry(myEntry, "local")));
      return entries;
    });
  }

  /**
   * A custom {@link AdbDeviceFileEntry} implementation for the the "/data/data" directory of a device.
   *
   * <p>The purpose is to allow file operations on files under "/data/data/packageName" using the
   * "run-as" command shell prefix.
   */
  private static class AdbDeviceDataDataDirectoryEntry extends AdbDeviceForwardingFileEntry {
    @NotNull
    private final AdbDeviceDirectFileEntry myDirectEntry;

    public AdbDeviceDataDataDirectoryEntry(@NotNull AdbDeviceFileEntry parent,
                                           @NotNull AdbFileListingEntry entry) {
      super(parent.myDevice, entry, parent);
      myDirectEntry = new AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, null);
    }

    @NotNull
    @Override
    public AdbDeviceFileEntry getForwardedFileEntry() {
      return myDirectEntry;
    }

    @NotNull
    @Override
    public ListenableFuture<List<DeviceFileEntry>> getEntries() {
      // Create an entry for each package returned by "pm list packages"
      ListenableFuture<List<String>> futurePackages = myDevice.getAdbFileOperations().listPackages();
      return myDevice.getTaskExecutor().transform(futurePackages, packages -> {
        assert packages != null;

        return packages.stream()
          .map(packageName -> new AdbDevicePackageDirectoryEntry(this, createDirectoryEntry(myEntry, packageName), packageName))
          .collect(Collectors.toList());
      });
    }
  }

  /**
   * A custom {@link AdbDeviceFileEntry} implementation for the the "/data/app" directory of a device.
   *
   * <p>The purpose is to allow file operations on files under "/data/app/packageName" using the
   * "run-as" command shell prefix.
   */
  private static class AdbDeviceDataAppDirectoryEntry extends AdbDeviceForwardingFileEntry {
    @NotNull
    private final AdbDeviceDirectFileEntry myDirectEntry;

    public AdbDeviceDataAppDirectoryEntry(@NotNull AdbDeviceFileEntry parent,
                                           @NotNull AdbFileListingEntry entry) {
      super(parent.myDevice, entry, parent);
      myDirectEntry = new AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, null);
    }

    @NotNull
    @Override
    public AdbDeviceFileEntry getForwardedFileEntry() {
      return myDirectEntry;
    }

    @NotNull
    @Override
    public ListenableFuture<List<DeviceFileEntry>> getEntries() {
      // Create an entry for each package returned by "pm list packages"
      ListenableFuture<List<AdbFileOperations.PackageInfo>> futurePackages = myDevice.getAdbFileOperations().listPackageInfo();
      return myDevice.getTaskExecutor().transform(futurePackages, packages -> {
        assert packages != null;

        return packages.stream()
          .map(info -> {
            List<String> segments = AdbPathUtil.getSegments(info.getPath());
            if (segments.size() <= 2) {
              return null;
            }
            if (!"data".equals(segments.get(0))) {
              return null;
            }
            if (!"app".equals(segments.get(1))) {
              return null;
            }
            if (segments.size() == 3) {
              // Some package paths are files directly inside the "/data/app" directory
              AdbFileListingEntry entry = createFileEntry(myEntry, segments.get(2));
              return new AdbDeviceDirectFileEntry(myDevice, entry, this, info.getPackageName());
            } else {
              // Most package paths are directories inside the "/data/app" directory
              AdbFileListingEntry entry = createDirectoryEntry(myEntry, segments.get(2));
              return new AdbDevicePackageDirectoryEntry(this, entry, info.getPackageName());
            }
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      });
    }
  }

  private static class AdbDeviceDataLocalDirectoryEntry extends AdbDeviceForwardingFileEntry {
    @NotNull
    private final AdbDeviceDirectFileEntry myDirectEntry;

    public AdbDeviceDataLocalDirectoryEntry(@NotNull AdbDeviceFileEntry parent,
                                           @NotNull AdbFileListingEntry entry) {
      super(parent.myDevice, entry, parent);
      myDirectEntry = new AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, null);
    }

    @NotNull
    @Override
    public AdbDeviceFileEntry getForwardedFileEntry() {
      return myDirectEntry;
    }

    @NotNull
    @Override
    public ListenableFuture<List<DeviceFileEntry>> getEntries() {
      return myDevice.getTaskExecutor().executeAsync(() -> {
        List<DeviceFileEntry> entries = new ArrayList<>();
        entries.add(new AdbDeviceDirectFileEntry(myDevice, createDirectoryEntry(myEntry, "tmp"), this, null));
        return entries;
      });
    }
  }

  /**
   * A custom {@link AdbDeviceFileEntry} implementation for a "/data/data/package-name" directory of a device.
   *
   * <p>Use the "run-as" command shell prefix for all file operations.
   */
  private static class AdbDevicePackageDirectoryEntry extends AdbDeviceForwardingFileEntry {
    @NotNull
    private final String myPackageName;
    @NotNull
    private final AdbDeviceDirectFileEntry myDirectEntry;

    public AdbDevicePackageDirectoryEntry(@NotNull AdbDeviceFileEntry parent,
                                          @NotNull AdbFileListingEntry entry,
                                          @NotNull String packageName) {
      super(parent.myDevice, entry, parent);
      myPackageName = packageName;
      myDirectEntry = new AdbDeviceDirectFileEntry(parent.myDevice, entry, parent, packageName);
    }

    @NotNull
    @Override
    public AdbDeviceFileEntry getForwardedFileEntry() {
      return myDirectEntry;
    }

    @NotNull
    @Override
    public ListenableFuture<List<DeviceFileEntry>> getEntries() {
      // Create "run-as" entries for child entries
      ListenableFuture<List<AdbFileListingEntry>> futureChildren =
        myDevice.getAdbFileListing().getChildrenRunAs(myEntry, myPackageName);

      return myDevice.getTaskExecutor().transform(futureChildren, entries -> {
        assert entries != null;
        return entries.stream().map(x -> new AdbDevicePackageDirectoryEntry(this, x, myPackageName)).collect(Collectors.toList());
      });
    }

    @NotNull
    @Override
    public ListenableFuture<Void> downloadFile(@NotNull Path localPath, @NotNull FileTransferProgress progress) {
      // Note: We should reach this code only if the device is not root, in which case
      // trying a "pullFile" would fail because of permission error (reading from the /data/data/
      // directory), so we copy the file to a temp. location, then pull from that temp location.
      return myDevice.getAdbFileTransfer().downloadFileViaTempLocation(getFullPath(), getSize(), localPath, progress, myPackageName);
    }

    @NotNull
    @Override
    public ListenableFuture<Void> uploadFile(@NotNull Path localPath, @NotNull String fileName, @NotNull FileTransferProgress progress) {
      // Note: We should reach this code only if the device is not root, in which case
      // trying a "pushFile" would fail because of permission error (writing to the /data/data/
      // directory), so we use the push to temporary location, then copy to final location.
      //
      // We do this directly instead of doing it as a fallback to attempting a regular push
      // because of https://code.google.com/p/android/issues/detail?id=241157.
      return myDevice.getAdbFileTransfer().uploadFileViaTempLocation(localPath,
                                                                     AdbPathUtil.resolve(getFullPath(), fileName),
                                                                     progress,
                                                                     myPackageName);
    }
  }
}