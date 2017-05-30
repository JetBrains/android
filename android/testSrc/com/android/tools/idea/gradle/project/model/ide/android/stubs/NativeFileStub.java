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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.NativeFile;

import java.io.File;
import java.util.Objects;

public class NativeFileStub extends BaseStub implements NativeFile {
  private final File myFilePath;
  private final String mySettingsName;
  private final File myWorkingDirectory;

  public NativeFileStub() {
    this(new File("filePath"), "settingsName", new File("workingDirectory"));
  }

  public NativeFileStub(File filePath, String settingsName, File workingDirectory) {
    myFilePath = filePath;
    mySettingsName = settingsName;
    myWorkingDirectory = workingDirectory;
  }

  @Override
  public File getFilePath() {
    return myFilePath;
  }

  @Override
  public String getSettingsName() {
    return mySettingsName;
  }

  @Override
  public File getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NativeFile)) {
      return false;
    }
    NativeFile file = (NativeFile)o;
    return Objects.equals(getFilePath(), file.getFilePath()) &&
           Objects.equals(getSettingsName(), file.getSettingsName()) &&
           Objects.equals(getWorkingDirectory(), file.getWorkingDirectory());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFilePath(), getSettingsName(), getWorkingDirectory());
  }
}
