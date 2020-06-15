/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the file type of TFLite model file.
 */
public class TfliteModelFileType implements FileType {
  public static final TfliteModelFileType INSTANCE = new TfliteModelFileType();
  private static final String TFLITE_EXTENSION = "tflite";

  @NotNull
  @Override
  public String getName() {
    return "TFLite model";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "TensorFlow Lite model file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return TFLITE_EXTENSION;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    // TODO(b/144867508): provide custom icon for ml model file.
    return null;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }
}
