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
import com.intellij.testFramework.LightVirtualFileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Implementation of an in-memory {@link VirtualFile} representing a directory. Unlike its base class,
 * it correctly implements the {#getParent()} method.
 */
public class ApkVirtualFolder extends LightVirtualFileBase {
  @Nullable private final Path parentPath;

  public ApkVirtualFolder(@NotNull String fileName, @Nullable Path parentPath) {
    super(fileName, null, -1);
    this.parentPath = parentPath;
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new IOException();
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    return new byte[0];
  }

  @Override
  public VirtualFile getParent() {
    return getDirectory(parentPath);
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  public static @Nullable VirtualFile getDirectory(@Nullable Path path) {
    if (path == null) {
      return null;
    }
    Path fileName = path.getFileName();
    if (fileName == null) {
      return null;
    }
    return new ApkVirtualFolder(fileName.toString(), path.getParent());
  }
}
