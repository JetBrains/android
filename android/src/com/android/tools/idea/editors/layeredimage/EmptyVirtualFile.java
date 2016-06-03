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


package com.android.tools.idea.editors.layeredimage;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

final class EmptyVirtualFile extends VirtualFile {
  private static final byte[] EMPTY_ARRAY = new byte[0];

  private final VirtualFile myFile;

  EmptyVirtualFile(VirtualFile file) {
    myFile = file;
  }

  @Override
  @NotNull
  public String getPath() {
    return myFile.getPath();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public VirtualFile[] getChildren() {
    return myFile.getChildren();
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return EMPTY_ARRAY;
  }

  @Override
  public long getTimeStamp() {
    return myFile.getTimeStamp();
  }

  @Override
  public long getLength() {
    return myFile.getLength();
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
    myFile.refresh(asynchronous, recursive, postRunnable);
  }

  @Override
  @NotNull
  public String getName() {
    return myFile.getName();
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isWritable() {
    return myFile.isWritable();
  }

  @Override
  public boolean isDirectory() {
    return myFile.isDirectory();
  }

  @Override
  public VirtualFile getParent() {
    return myFile.getParent();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    return new ByteArrayOutputStream();
  }

  @Override
  public long getModificationStamp() {
    return myFile.getModificationStamp();
  }
}
