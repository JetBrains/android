/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.gradle;

import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class IdeaSingleEntryLibraryDependencyStub implements IdeaSingleEntryLibraryDependency {
  @NotNull private final File myFile;

  @Nullable private File mySource;
  @Nullable private File myJavadoc;

  public IdeaSingleEntryLibraryDependencyStub(@NotNull File file) {
    myFile = file;
  }

  @Override
  public File getFile() {
    return myFile;
  }

  public void setSource(@NotNull File source) {
    mySource = source;
  }

  @Override
  public File getSource() {
    return mySource;
  }

  public void setJavadoc(@NotNull File javadoc) {
    myJavadoc = javadoc;
  }

  @Override
  public File getJavadoc() {
    return myJavadoc;
  }

  @Override
  public GradleModuleVersion getGradleModuleVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IdeaDependencyScope getScope() {
    return IdeaDependencyScopeStub.COMPILE;
  }

  @Override
  public boolean getExported() {
    return false;
  }

  @Override
  public boolean isExported() {
    return getExported();
  }

}
