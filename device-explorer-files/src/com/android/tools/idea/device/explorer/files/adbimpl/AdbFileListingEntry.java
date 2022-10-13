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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a file entry in the file system of a device:
 * Once acquired, a instance of {@link AdbFileListingEntry} never changes
 * and is not linked back to the originating device.
 */
public class AdbFileListingEntry {
  @NotNull private static Logger LOGGER = Logger.getInstance(AdbFileListingEntry.class);

  @NotNull private final String myPath;
  @NotNull private final EntryKind myKind;
  @Nullable private final String myPermissions;
  @Nullable private final String myOwner;
  @Nullable private final String myGroup;
  @Nullable private final String myDate;
  @Nullable private final String myTime;
  @Nullable private final String mySize;
  @Nullable private final String myExtraInfo;

  public enum EntryKind {
    /** A regular file, corresponds to the "{@code -}" prefix in the list of attributes returned by "{@code ls -l}". */
    FILE,

    /** A directory, corresponds to the "{@code d}" prefix in the list of attributes returned by "{@code ls -l}". */
    DIRECTORY,

    /** A symbolic link, corresponds to the "{@code l}" prefix in the list of attributes returned by "{@code ls -l}". */
    SYMBOLIC_LINK,

    /** A block file, corresponds to the "{@code b}" prefix in the list of attributes returned by "{@code ls -l}". */
    BLOCK,

    /** A character file, corresponds to the "{@code c}" prefix in the list of attributes returned by "{@code ls -l}". */
    CHARACTER,

    /** A socket file, corresponds to the "{@code a}" prefix in the list of attributes returned by "{@code ls -l}". */
    SOCKET,

    /** A FIFO/pipe file, corresponds to the "{@code p}" prefix in the list of attributes returned by "{@code ls -l}". */
    FIFO,

    /** A unknown file type, when the prefix in the list of attributes returned by "{@code ls -l}" is not known. */
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

  @Override
  public String toString() {
    return String.format("%s: %s", myKind, myPath);
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

  public long getSize() {
    String size = mySize;
    if (StringUtil.isEmpty(size)) {
      return -1;
    }

    // For character devices, size is of the form "xxx, yyy".
    int index = size.indexOf(',');
    if (index >= 0) {
      return -1;
    }

    try {
      return Long.parseLong(size);
    }
    catch (NumberFormatException e) {
      LOGGER.warn(String.format("Error paring size string \"%s\"", size), e);
      return -1;
    }
  }

  @Nullable
  public String getInfo() {
    return myExtraInfo;
  }

  public boolean isDirectory() {
    return myKind == EntryKind.DIRECTORY;
  }

  public boolean isFile() {
    return myKind == EntryKind.FILE;
  }

  public boolean isSymbolicLink() {
    return myKind == EntryKind.SYMBOLIC_LINK;
  }
}
