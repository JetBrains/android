/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.ui;

import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public class LabelAndFileForLocation {
  @NotNull private String myLabel;
  @NotNull private Path myFile;

  public LabelAndFileForLocation(@NotNull String label, @NotNull Path file) {
    myLabel = label;
    myFile = file;
  }

  @NotNull
  public String getLabel() {
    return myLabel;
  }

  @NotNull
  public Path getFile() {
    return myFile;
  }

  @NotNull
  public String getSystemDependentPath() {
    return toSystemDependentName(myFile.toString());
  }

  @Override
  public String toString() {
    return myLabel + ": " + getSystemDependentPath();
  }
}
