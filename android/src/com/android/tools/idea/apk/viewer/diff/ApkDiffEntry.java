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
package com.android.tools.idea.apk.viewer.diff;

import com.android.tools.idea.apk.viewer.ApkEntry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ApkDiffEntry implements ApkEntry {
  private String myName;
  @Nullable private final VirtualFile myOldFile;
  @Nullable private final VirtualFile myNewFile;
  private final long myOldSize;
  private final long myNewSize;

  ApkDiffEntry(@NotNull String name, @Nullable VirtualFile oldFile, @Nullable VirtualFile newFile, long oldSize, long newSize) {
    this.myName = name;
    this.myOldFile = oldFile;
    this.myNewFile = newFile;
    this.myOldSize = oldSize;
    this.myNewSize = newSize;
  }

  @Override
  public boolean isCompressedSizeKnown() {
    return false;
  }

  @Override
  public long getCompressedSize() {
    return 0;
  }

  @Override
  public void setCompressedSize(long compressedSize) {

  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    if (myOldFile == null) {
      assert myNewFile != null;
      return myNewFile;
    }
    else {
      return myOldFile;
    }
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getPath() {
    return getFile().getPath();
  }

  @Override
  public long getSize() {
    return myNewSize - myOldSize;
  }

  public long getOldSize() {
    return myOldSize;
  }

  public long getNewSize() {
    return myNewSize;
  }

  public static long getOldSize(ApkEntry apkEntry) {
    if (apkEntry instanceof ApkDiffEntry) {
      return ((ApkDiffEntry)apkEntry).getOldSize();
    }
    return apkEntry.getSize();
  }

  public static long getNewSize(ApkEntry apkEntry) {
    if (apkEntry instanceof ApkDiffEntry) {
      return ((ApkDiffEntry)apkEntry).getNewSize();
    }
    return apkEntry.getSize();
  }
}
