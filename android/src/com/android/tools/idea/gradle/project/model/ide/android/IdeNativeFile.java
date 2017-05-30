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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.NativeFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

public final class IdeNativeFile extends IdeModel implements NativeFile {
  private final File myFilePath;
  private final String mySettingsName;
  private final File myWorkingDirectory;
  private final int myHashCode;

  public IdeNativeFile(@NotNull NativeFile file, @NotNull ModelCache modelCache) {
    super(file, modelCache);
    myFilePath = file.getFilePath();
    mySettingsName = file.getSettingsName();
    myWorkingDirectory = file.getWorkingDirectory();
    myHashCode = calculateHashCode();
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
    if (!(o instanceof IdeNativeFile)) {
      return false;
    }
    IdeNativeFile file = (IdeNativeFile)o;
    return Objects.equals(myFilePath, file.myFilePath) &&
           Objects.equals(mySettingsName, file.mySettingsName) &&
           Objects.equals(myWorkingDirectory, file.myWorkingDirectory);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myFilePath, mySettingsName, myWorkingDirectory);
  }

  @Override
  public String toString() {
    return "IdeNativeFile{" +
           "myFilePath=" + myFilePath +
           ", mySettingsName='" + mySettingsName + '\'' +
           ", myWorkingDirectory=" + myWorkingDirectory +
           "}";
  }
}
