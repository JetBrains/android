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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

public final class DependenciesStub extends BaseStub implements Dependencies {
  @NotNull private final Collection<AndroidLibrary> myLibraries;
  @NotNull private final Collection<JavaLibrary> myJavaLibraries;
  @NotNull private final Collection<String> myProjects;

  public DependenciesStub() {
    this(Lists.newArrayList(), Lists.newArrayList(new JavaLibraryStub()), Lists.newArrayList("project1", "project2"));
  }

  public DependenciesStub(@NotNull Collection<AndroidLibrary> libraries,
                          @NotNull Collection<JavaLibrary> javaLibraries,
                          @NotNull Collection<String> projects) {
    myLibraries = libraries;
    myJavaLibraries = javaLibraries;
    myProjects = projects;
  }

  @Override
  @NotNull
  public Collection<AndroidLibrary> getLibraries() {
    return myLibraries;
  }

  @Override
  @NotNull
  public Collection<JavaLibrary> getJavaLibraries() {
    return myJavaLibraries;
  }

  @Override
  @NotNull
  public Collection<String> getProjects() {
    return myProjects;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Dependencies)) {
      return false;
    }
    Dependencies stub = (Dependencies)o;
    return Objects.equals(getLibraries(), stub.getLibraries()) &&
           Objects.equals(getJavaLibraries(), stub.getJavaLibraries()) &&
           Objects.equals(getProjects(), stub.getProjects());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getLibraries(), getJavaLibraries(), getProjects());
  }

  @Override
  public String toString() {
    return "DependenciesStub{" +
           "myLibraries=" + myLibraries +
           ", myJavaLibraries=" + myJavaLibraries +
           ", myProjects=" + myProjects +
           "}";
  }
}
