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

public interface ApkEntry {

  @Nullable
  static ApkEntry fromNode(@Nullable Object value) {
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

  boolean isCompressedSizeKnown();

  long getCompressedSize();

  void setCompressedSize(long compressedSize);

  @NotNull
  String getName();

  @NotNull
  VirtualFile getFile();

  @NotNull
  String getPath();

  long getSize();
}
