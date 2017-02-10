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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a file entry in the file system of a device:
 * Once acquired, a instance of {@link AdbFileListingEntry} never changes
 * and is not linked back to the originating device.
 */
public class AdbFileListingEntry {
  @NotNull private final String myPath;
  @NotNull private EntryKind myKind;
  @Nullable private final String myPermissions;
  @Nullable private final String myOwner;
  @Nullable private final String myGroup;
  @Nullable private final String myDate;
  @Nullable private final String myTime;
  @Nullable private final String mySize;
  @Nullable private final String myExtraInfo;

  public enum EntryKind {
    FILE,
    DIRECTORY,
    FILE_LINK,
    DIRECTORY_LINK,
    BLOCK,
    CHARACTER,
    SOCKET,
    FIFO,
    OTHER,
  }

  public AdbFileListingEntry(@NotNull String path,
                             @NotNull EntryKind kind,
                             @Nullable String permissions,
                             @Nullable String owner,
                             @Nullable String group,
                             @Nullable String date,
                             @Nullable String time,
                             @Nullable String size,
                             @Nullable String extraInfo) {
    myPath = path;
    myKind = kind;
    myPermissions = permissions;
    myOwner = owner;
    myGroup = group;
    myDate = date;
    myTime = time;
    mySize = size;
    myExtraInfo = extraInfo;
  }

  @NotNull
  public String getFullPath() {
    return myPath;
  }

  @NotNull
  public String getName() {
    return AdbPathUtil.getFileName(myPath);
  }

  @NotNull
  public EntryKind getKind() {
    return myKind;
  }

  @Nullable
  public String getPermissions() {
    return myPermissions;
  }

  @Nullable
  public String getOwner() {
    return myOwner;
  }

  @Nullable
  public String getGroup() {
    return myGroup;
  }

  @Nullable
  public String getDate() {
    return myDate;
  }

  @Nullable
  public String getTime() {
    return myTime;
  }

  @Nullable
  public String getSize() {
    return mySize;
  }

  @Nullable
  public String getInfo() {
    return myExtraInfo;
  }

  public boolean isDirectory() {
    return myKind == EntryKind.DIRECTORY || myKind == EntryKind.DIRECTORY_LINK;
  }

  public boolean isFile() {
    return myKind == EntryKind.FILE || myKind == EntryKind.FILE_LINK;
  }

  public boolean isSymbolicLink() {
    return myKind == EntryKind.FILE_LINK || myKind == EntryKind.DIRECTORY_LINK;
  }
}
