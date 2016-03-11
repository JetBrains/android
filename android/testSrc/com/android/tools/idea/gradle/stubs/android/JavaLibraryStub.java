/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class JavaLibraryStub implements JavaLibrary {
  @NotNull private final File myJarFile;

  public JavaLibraryStub(@NotNull File jarFile) {
    myJarFile = jarFile;
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public List<? extends JavaLibrary> getDependencies() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isProvided() {
    return false;
  }

  @Override
  @Nullable
  public String getProject() {
    return null;
  }

  @Override
  @Nullable
  public String getName() {
    return null;
  }

  @Override
  @Nullable
  public MavenCoordinates getRequestedCoordinates() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public MavenCoordinates getResolvedCoordinates() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSkipped() {
    return false;
  }
}
