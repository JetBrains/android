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
package com.android.tools.idea.apk.viewer;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ApkEntryImpl implements ApkEntry {
  private final VirtualFile myFile;
  @Nullable private final String myOriginalName; // original name if different from myFile.getName()
  private final long mySize;

  private long myCompressedSize = -1;

  ApkEntryImpl(@NotNull VirtualFile file, @Nullable String originalName, long size) {
    this.myFile = file;
    this.myOriginalName = originalName;
    this.mySize = size;
  }

  @Override
  public boolean isCompressedSizeKnown() {
    return myCompressedSize >= 0;
  }

  @Override
  public long getCompressedSize() {
    return myCompressedSize;
  }

  @Override
  public void setCompressedSize(long compressedSize) {
    myCompressedSize = compressedSize;
  }

  @NotNull
  @Override
  public String getName() {
    return myOriginalName != null ? myOriginalName : myFile.getName();
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  @Override
  public String getPath() {
    return myFile.getPath();
  }

  @Override
  public long getSize() {
    return mySize;
  }
}
