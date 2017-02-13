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

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class AdbDeviceFileEntry implements DeviceFileEntry {
  public static final String SYMBOLIC_LINK_INFO_PREFIX = "-> ";

  @NotNull protected final AdbDeviceFileSystem myDevice;
  @NotNull protected final AdbFileListingEntry myEntry;
  @Nullable protected final AdbDeviceFileEntry myParent;

  public AdbDeviceFileEntry(@NotNull AdbDeviceFileSystem device,
                            @NotNull AdbFileListingEntry entry,
                            @Nullable AdbDeviceFileEntry parent) {
    myDevice = device;
    myEntry = entry;
    myParent = parent;
  }

  @NotNull
  @Override
  public DeviceFileSystem getFileSystem() {
    return myDevice;
  }

  @Nullable
  @Override
  public AdbDeviceFileEntry getParent() {
    return myParent;
  }

  @NotNull
  @Override
  public String getName() {
    return myEntry.getName();
  }

  @NotNull
  @Override
  public Permissions getPermissions() {
    return new AdbPermissions(myEntry.getPermissions());
  }

  @NotNull
  @Override
  public DateTime getLastModifiedDate() {
    return new AdbDateTime(myEntry.getDate(), myEntry.getTime());
  }

  @Override
  public long getSize() {
    String size = myEntry.getSize();
    if (StringUtil.isEmpty(size)) {
      return -1;
    }

    // For character devices, size is of the form "xxx, yyy".
    int index = size.indexOf(",");
    if (index >= 0) {
      return -1;
    }

    try {
      return Long.parseLong(size);
    }
    catch (NumberFormatException e) {
      AdbDeviceFileSystemService.LOGGER.warn(String.format("Error paring size string \"%s\"", size), e);
      return -1;
    }
  }

  @Override
  public boolean isDirectory() {
    return myEntry.isDirectory();
  }

  @Override
  public boolean isFile() {
    return myEntry.isFile();
  }

  @Override
  public boolean isSymbolicLink() {
    return myEntry.isSymbolicLink();
  }

  @Nullable
  @Override
  public String getSymbolicLinkTarget() {
    if (!isSymbolicLink()) {
      return null;
    }

    String info = myEntry.getInfo();
    if (StringUtil.isEmpty(info) || !info.startsWith(SYMBOLIC_LINK_INFO_PREFIX)) {
      return null;
    }
    return info.substring(SYMBOLIC_LINK_INFO_PREFIX.length());
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    SettableFuture<List<DeviceFileEntry>> futureResult = SettableFuture.create();

    ListenableFuture<List<AdbFileListingEntry>> futureChildren = myDevice.getAdbFileListing().getChildren(myEntry);
    myDevice.getTaskExecutor().addCallback(futureChildren, new FutureCallback<List<AdbFileListingEntry>>() {
      @Override
      public void onSuccess(@Nullable List<AdbFileListingEntry> result) {
        assert result != null;
        List<DeviceFileEntry> children = result.stream()
          .map(x -> new AdbDeviceFileEntry(myDevice, x, AdbDeviceFileEntry.this))
          .collect(Collectors.toList());
        futureResult.set(children);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
 }

  public static class AdbPermissions implements Permissions {
    private final String myValue;

    public AdbPermissions(String value) {
      myValue = value;
    }

    @NotNull
    @Override
    public String getText() {
      return StringUtil.notNullize(myValue);
    }
  }

  public static class AdbDateTime implements DateTime {
    private final String myDate;
    private final String myTime;

    public AdbDateTime(String date, String time) {
      myDate = date;
      myTime = time;
    }

    @NotNull
    @Override
    public String getText() {
      if (StringUtil.isEmpty(myDate)) {
        return "";
      }
      if (StringUtil.isEmpty(myTime)) {
        return myDate;
      }

      return myDate + " " + myTime;
    }
  }
}
