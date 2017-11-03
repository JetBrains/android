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
package com.android.tools.io;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import com.google.common.base.Objects;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene.Kudelevsky
 */
public class BufferingFileWrapper implements IAbstractFile {
  private final File myFile;

  public BufferingFileWrapper(@NotNull File file) {
    myFile = file;
  }

  @Override
  public InputStream getContents() throws StreamException {
    // it's not very good idea to return unclosed InputStream and entrust its closing to library, so let's read whole file
    try {
      final byte[] content = readFile();
      return new ByteArrayInputStream(content);
    }
    catch (IOException e) {
      throw new StreamException(e, this);
    }
  }

  private byte[] readFile() throws IOException {
    DataInputStream is = new DataInputStream(new FileInputStream(myFile));
    try {
      byte[] data = new byte[(int)myFile.length()];
      //noinspection ResultOfMethodCallIgnored
      is.readFully(data);
      return data;
    }
    finally {
      is.close();
    }
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @Override
  public void setContents(InputStream source) throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutputStream getOutputStream() throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PreferredWriteMode getPreferredWriteMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getModificationStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getOsLocation() {
    return myFile.getAbsolutePath();
  }

  @Override
  public String getPath() {
    return myFile.getPath();
  }

  @Override
  public boolean exists() {
    return myFile.isFile();
  }

  @Nullable
  @Override
  public IAbstractFolder getParentFolder() {
    final File parentFile = myFile.getParentFile();
    return parentFile != null ? new BufferingFolderWrapper(parentFile) : null;
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BufferingFileWrapper wrapper = (BufferingFileWrapper)o;

    return FileUtil.filesEqual(myFile, wrapper.myFile);
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(myFile);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this.getClass().getName()).add("file", myFile).toString();
  }
}
