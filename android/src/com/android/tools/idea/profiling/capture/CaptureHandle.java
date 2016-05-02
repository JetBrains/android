/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.capture;

import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * An opaque handle that holds references to the resources needed to work with the backing file.
 */
public class CaptureHandle {
  @NotNull private File myFile;
  @NotNull private CaptureType myType;
  private boolean myWriteToTempFile;
  @Nullable private volatile FileOutputStream myFileOutputStream;

  CaptureHandle(@NotNull File file, @NotNull CaptureType type, boolean writeToTempFile) throws IOException {
    myFile = file;
    myType = type;
    myWriteToTempFile = writeToTempFile;
    myFileOutputStream = new FileOutputStream(myFile, true);
  }

  @NotNull
  File getFile() {
    return myFile;
  }

  @NotNull
  CaptureType getCaptureType() {
    return myType;
  }

  @Nullable
  FileOutputStream getFileOutputStream() {
    return myFileOutputStream;
  }

  boolean getWriteToTempFile() {
    return myWriteToTempFile;
  }

  void closeFileOutputStream() {
    FileOutputStream fileOutputStream = myFileOutputStream;
    if (fileOutputStream != null) {
      try {
        fileOutputStream.close();
      }
      catch (IOException ignored) {
      }
      myFileOutputStream = null;
    }
  }

  boolean isWritable() {
    return myFileOutputStream != null;
  }

  public void move(File file) throws IOException {
    Files.move(myFile, file);
    myFile = file;
  }
}
