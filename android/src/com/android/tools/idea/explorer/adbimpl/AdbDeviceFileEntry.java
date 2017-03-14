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
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base class for all implementations of {@link DeviceFileEntry} that rely on
 * an underlying {@link AdbFileListingEntry}.
 */
public abstract class AdbDeviceFileEntry implements DeviceFileEntry {
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

  @Override
  public String toString() {
    return myEntry.toString();
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
  public String getFullPath() {
    return myEntry.getFullPath();
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
    return myEntry.getSize();
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
