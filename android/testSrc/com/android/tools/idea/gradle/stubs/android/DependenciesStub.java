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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidLibrary;
import com.android.tools.idea.gradle.project.model.ide.android.IdeDependencies;
import com.android.tools.idea.gradle.project.model.ide.android.IdeJavaLibrary;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class DependenciesStub implements IdeDependencies {
  @NotNull private final List<AndroidLibrary> myLibraries = Lists.newArrayList();
  @NotNull private final List<JavaLibrary> myJavaLibraries = Lists.newArrayList();
  @NotNull private final List<String> myProjects = Lists.newArrayList();

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

  @Override
  public void forEachLibrary(@NotNull Consumer<IdeAndroidLibrary> action) {
  }

  @Override
  public void forEachJavaLibrary(@NotNull Consumer<IdeJavaLibrary> action) {
  }
}
