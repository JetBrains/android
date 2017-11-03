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

import com.android.builder.model.NativeFolder;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public class NativeFolderStub extends BaseStub implements NativeFolder {
  private final File myFolderPath;
  private final Map<String, String> myPerLanguageSettings;
  private final File myWorkingDirectory;

  public NativeFolderStub() {
    this(new File("folder"), ImmutableMap.<String, String>builder().put("key", "value").build(), new File("workingDirectory"));
  }

  public NativeFolderStub(File folderPath, Map<String, String> settings, File workingDirectory) {
    myFolderPath = folderPath;
    myPerLanguageSettings = settings;
    myWorkingDirectory = workingDirectory;
  }

  @Override
  public File getFolderPath() {
    return myFolderPath;
  }

  @Override
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
    if (!(o instanceof NativeFolder)) {
      return false;
    }
    NativeFolder folder = (NativeFolder)o;
    return Objects.equals(getFolderPath(), folder.getFolderPath()) &&
           Objects.equals(getPerLanguageSettings(), folder.getPerLanguageSettings()) &&
           Objects.equals(getWorkingDirectory(), folder.getWorkingDirectory());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFolderPath(), getPerLanguageSettings(), getWorkingDirectory());
  }
}
