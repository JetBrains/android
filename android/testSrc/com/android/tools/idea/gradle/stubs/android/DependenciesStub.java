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
package com.android.tools.idea.gradle.stubs.android;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DependenciesStub implements Dependencies {
  @NotNull private final List<AndroidLibrary> myLibraries = new ArrayList<>();
  @NotNull private final List<JavaLibrary> myJavaLibraries = new ArrayList<>();
  @NotNull private final List<String> myProjects = new ArrayList<>();
  @NotNull private final List<File> myRuntimeOnlyClasses = new ArrayList<>();

  public void addLibrary(@NotNull AndroidLibraryStub library) {
    myLibraries.add(library);
  }

  @Override
  @NotNull
  public List<AndroidLibrary> getLibraries() {
    return myLibraries;
  }

  @Override
  @NotNull
  public Collection<JavaLibrary> getJavaLibraries() {
    return myJavaLibraries;
  }

  public void addJar(@NotNull File jar) {
    myJavaLibraries.add(new JavaLibraryStub(jar));
  }

  public void addProject(@NotNull String project) {
    myProjects.add(project);
  }

  @Override
  @NotNull
  public List<String> getProjects() {
    return myProjects;
  }

  @NonNull
  @Override
  public Collection<ProjectIdentifier> getJavaModules() {
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public Collection<File> getRuntimeOnlyClasses() {
    return myRuntimeOnlyClasses;
  }
}
