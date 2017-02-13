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

import com.android.ddmlib.FileListingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.ddmlib.FileListingService.TYPE_DIRECTORY_LINK;
import static com.android.ddmlib.FileListingService.TYPE_FILE;
import static com.android.ddmlib.FileListingService.TYPE_LINK;

public class AdbFileListingEntry {
  @NotNull private FileListingService.FileEntry myAdbEntry;

  public AdbFileListingEntry(@NotNull FileListingService.FileEntry adbEntry) {
    myAdbEntry = adbEntry;
  }

  @NotNull
  public FileListingService.FileEntry getAdbEntry() {
    return myAdbEntry;
  }

  @NotNull
  public String getName() {
    return myAdbEntry.getName();
  }

  @Nullable
  public String getPermissions() {
    return myAdbEntry.getPermissions();
  }

  @Nullable
  public String getDate() {
    return myAdbEntry.getDate();
  }

  @Nullable
  public String getTime() {
    return myAdbEntry.getTime();
  }

  @Nullable
  public String getSize() {
    return myAdbEntry.getSize();
  }

  @Nullable
  public String getInfo() {
    return myAdbEntry.getInfo();
  }

  @NotNull
  public String getFullPath() {
    return myAdbEntry.getFullPath();
  }

  public boolean isDirectory() {
    return myAdbEntry.isDirectory();
  }

  public boolean isFile() {
    return myAdbEntry.getType() == TYPE_FILE || myAdbEntry.getType() == TYPE_LINK;
  }

  public boolean isSymbolicLink() {
    return myAdbEntry.getType() == TYPE_DIRECTORY_LINK || myAdbEntry.getType() == TYPE_LINK;
  }
}
