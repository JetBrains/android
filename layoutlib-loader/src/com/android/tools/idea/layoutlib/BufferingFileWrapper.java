/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutlib;

import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.google.common.base.MoreObjects;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;

class BufferingFileWrapper implements IAbstractFile {
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
  public String getOsLocation() {
    return myFile.getAbsolutePath();
  }

  @Override
  public boolean exists() {
    return myFile.isFile();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this.getClass()).add("file", myFile).toString();
  }
}
