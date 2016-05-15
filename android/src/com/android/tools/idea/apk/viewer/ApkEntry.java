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

import javax.swing.tree.DefaultMutableTreeNode;

final class ApkEntry {
  private static final long UNKNOWN = -1;

  public final VirtualFile file;
  @Nullable private final String originalName; // original name if different from file.getName()
  public final long size;

  private long myCompressedSize = UNKNOWN;

  ApkEntry(@NotNull VirtualFile file, @Nullable String originalName, long size) {
    this.file = file;
    this.originalName = originalName;
    this.size = size;
  }

  @Nullable
  public static ApkEntry fromNode(@Nullable Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) {
      return null;
    }

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ApkEntry)) {
      return null;
    }

    return (ApkEntry)userObject;
  }

  public boolean isCompressedSizeKnown() {
    return myCompressedSize != UNKNOWN;
  }

  public long getCompressedSize() {
    return myCompressedSize;
  }

  public void setCompressedSize(long compressedSize) {
    myCompressedSize = compressedSize;
  }

  @NotNull
  public String getName() {
    return originalName != null ? originalName : file.getName();
  }
}
