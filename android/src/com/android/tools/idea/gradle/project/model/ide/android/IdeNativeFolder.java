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

import com.android.builder.model.NativeFolder;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class IdeNativeFolder extends IdeModel implements NativeFolder {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  private final File myFolderPath;
  private final Map<String, String> myPerLanguageSettings;
  private final File myWorkingDirectory;
  private final int myHashCode;

  public IdeNativeFolder(@NotNull NativeFolder folder, @NotNull ModelCache modelCache) {
    super(folder, modelCache);
    myFolderPath = folder.getFolderPath();

    Map<String, String> settings = folder.getPerLanguageSettings();
    myPerLanguageSettings = settings != null ? ImmutableMap.copyOf(settings) : Collections.emptyMap();

    myWorkingDirectory = folder.getWorkingDirectory();
    myHashCode = calculateHashCode();
  }

  @Override
  public File getFolderPath() {
    return myFolderPath;
  }

  @Override
  @NotNull
  public Map<String, String> getPerLanguageSettings() {
    return myPerLanguageSettings;
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
    if (!(o instanceof IdeNativeFolder)) {
      return false;
    }
    IdeNativeFolder folder = (IdeNativeFolder)o;
    return Objects.equals(myFolderPath, folder.myFolderPath) &&
           Objects.equals(myPerLanguageSettings, folder.myPerLanguageSettings) &&
           Objects.equals(myWorkingDirectory, folder.myWorkingDirectory);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myFolderPath, myPerLanguageSettings, myWorkingDirectory);
  }

  @Override
  public String toString() {
    return "IdeNativeFolder{" +
           "myFolderPath=" + myFolderPath +
           ", myPerLanguageSettings=" + myPerLanguageSettings +
           ", myWorkingDirectory=" + myWorkingDirectory +
           "}";
  }
}
