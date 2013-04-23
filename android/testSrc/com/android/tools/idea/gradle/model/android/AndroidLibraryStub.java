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
package com.android.tools.idea.gradle.model.android;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class AndroidLibraryStub implements AndroidLibrary {
  @NotNull private final File myJarFile;

  public AndroidLibraryStub(@NotNull File jarFile) {
    myJarFile = jarFile;
  }

  @NonNull
  @Override
  public File getFolder() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<? extends AndroidLibrary> getLibraryDependencies() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getJarFile() {
    return myJarFile;
  }

  @NonNull
  @Override
  public List<File> getLocalJars() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getResFolder() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getAssetsFolder() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getJniFolder() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getAidlFolder() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getRenderscriptFolder() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getProguardRules() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getLintJar() {
    throw new UnsupportedOperationException();
  }
}
