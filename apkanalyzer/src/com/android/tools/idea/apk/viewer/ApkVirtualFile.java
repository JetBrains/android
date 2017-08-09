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
package com.android.tools.idea.apk.viewer;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.BinaryLightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Implementation of an in-memory {@link VirtualFile} that correctly implements the {#getParent()} method.
 */
class ApkVirtualFile extends BinaryLightVirtualFile {
  @Nullable private final Path parentPath;

  public ApkVirtualFile(@NotNull String filename, @Nullable Path parentPath, @NotNull byte[] content) {
    super(filename, content);
    this.parentPath = parentPath;
  }

  @Override
  public VirtualFile getParent() {
    return ApkVirtualFolder.getDirectory(parentPath);
  }

  @Nullable public static ApkVirtualFile create(@NotNull Path path, @NotNull byte[] content) {
    Path fileName = path.getFileName();
    if (fileName == null) {
      return null;
    }
    return new ApkVirtualFile(fileName.toString(), path.getParent(), content);
  }

}
