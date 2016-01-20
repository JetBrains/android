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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.NativeFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class NativeFileStub implements NativeFile {
  @NotNull private final File myFilePath;

  public NativeFileStub(@NotNull File filePath) {
    myFilePath = filePath;
  }

  @Override
  @NotNull
  public File getFilePath() {
    return myFilePath;
  }

  @Override
  @NotNull
  public String getSettingsName() {
    return "";
  }

  @Override
  public File getWorkingDirectory() {
    throw new UnsupportedOperationException();
  }
}
