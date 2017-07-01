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
package com.android.tools.idea.gradle.project.model.ide.android.stubs.level2;

import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependencies;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class IdeDependenciesStub implements IdeDependencies {
  @NotNull private final Collection<Library> myAndroidLibraries;
  @NotNull private final Collection<Library> myJavaLibraries;
  @NotNull private final Collection<Library> myModuleDependencies;

  public IdeDependenciesStub() {
    this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

  IdeDependenciesStub(@NotNull Collection<Library> androidLibraries,
                      @NotNull Collection<Library> javaLibraries,
                      @NotNull Collection<Library> moduleDependencies) {
    myAndroidLibraries = androidLibraries;
    myJavaLibraries = javaLibraries;
    myModuleDependencies = moduleDependencies;
  }

  @Override
  @NotNull
  public Collection<Library> getAndroidLibraries() {
    return myAndroidLibraries;
  }

  @Override
  @NotNull
  public Collection<Library> getJavaLibraries() {
    return myJavaLibraries;
  }

  @Override
  @NotNull
  public Collection<Library> getModuleDependencies() {
    return myModuleDependencies;
  }

  public void addAndroidLibrary(@NotNull Library library) {
    myAndroidLibraries.add(library);
  }

  public void addJavaLibrary(@NotNull Library library) {
    myJavaLibraries.add(library);
  }

  public void addModuleDependency(@NotNull Library library) {
    myModuleDependencies.add(library);
  }
}
