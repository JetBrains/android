/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.adbimpl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdbFileListingEntryBuilder {
  @Nullable private String myPath;
  @Nullable private AdbFileListingEntry.EntryKind myKind;
  @Nullable private String myPermissions;
  @Nullable private String myOwner;
  @Nullable private String myGroup;
  @Nullable private String myDate;
  @Nullable private String myTime;
  private long mySize;
  @Nullable private String myExtraInfo;

  public AdbFileListingEntryBuilder() {
  }

  public AdbFileListingEntryBuilder(@NotNull AdbFileListingEntry source) {
    myPath = source.getFullPath();
    myKind = source.getKind();
    myPermissions = source.getPermissions();
    myOwner = source.getOwner();
    myGroup = source.getGroup();
    myDate = source.getDate();
    myTime = source.getTime();
    mySize = source.getSize();
    myExtraInfo = source.getInfo();
  }

  public AdbFileListingEntry build() {
    if (myPath == null) {
      throw new IllegalStateException("Entry path must be set");
    }
    if (myKind == null) {
      throw new IllegalStateException("Entry kind must be set");
    }
    return new AdbFileListingEntry(myPath, myKind, myPermissions, myOwner, myGroup, myDate, myTime, Long.toString(mySize), myExtraInfo);
  }

  public AdbFileListingEntryBuilder setPath(@NotNull String path) {
    myPath = path;
    return this;
  }

  public AdbFileListingEntryBuilder setKind(@NotNull AdbFileListingEntry.EntryKind kind) {
    myKind = kind;
    return this;
  }

  public AdbFileListingEntryBuilder setPermissions(@Nullable String permissions) {
    myPermissions = permissions;
    return this;
  }

  public AdbFileListingEntryBuilder setOwner(@Nullable String owner) {
    myOwner = owner;
    return this;
  }

  public AdbFileListingEntryBuilder setGroup(@Nullable String group) {
    myGroup = group;
    return this;
  }

  public AdbFileListingEntryBuilder setDate(@Nullable String date) {
    myDate = date;
    return this;
  }

  public AdbFileListingEntryBuilder setTime(@Nullable String time) {
    myTime = time;
    return this;
  }

  public AdbFileListingEntryBuilder setSize(long size) {
    mySize = size;
    return this;
  }

  @SuppressWarnings("unused")
  public AdbFileListingEntryBuilder setExtraInfo(@Nullable String extraInfo) {
    myExtraInfo = extraInfo;
    return this;
  }
}
