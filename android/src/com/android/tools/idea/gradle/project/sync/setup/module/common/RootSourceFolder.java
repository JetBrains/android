/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;

public class RootSourceFolder {
  @NotNull private final File myPath;
  @NotNull private final JpsModuleSourceRootType myType;

  private final boolean myGenerated;

  public RootSourceFolder(@NotNull File path, @NotNull JpsModuleSourceRootType type, boolean generated) {
    myPath = path;
    myType = type;
    myGenerated = generated;
  }

  @NotNull
  public File getPath() {
    return myPath;
  }

  @NotNull
  public JpsModuleSourceRootType getType() {
    return myType;
  }

  public boolean isGenerated() {
    return myGenerated;
  }
}
