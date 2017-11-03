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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Creates a deep copy of a {@link Dependencies}.
 */
public final class IdeDependenciesImpl extends IdeModel implements IdeDependencies {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final Collection<AndroidLibrary> myLibraries;
  @NotNull private final Collection<JavaLibrary> myJavaLibraries;
  @NotNull private final Collection<String> myProjects;
  private final int myHashCode;

  public IdeDependenciesImpl(@NotNull Dependencies dependencies, @NotNull ModelCache modelCache, @Nullable GradleVersion modelVersion) {
    super(dependencies, modelCache);

    myLibraries = copy(dependencies.getLibraries(), modelCache, library -> new IdeAndroidLibrary(library, modelCache));
    myJavaLibraries = copy(dependencies.getJavaLibraries(), modelCache, library -> new IdeJavaLibrary(library, modelCache));

    myProjects = ImmutableList.copyOf(dependencies.getProjects());

    myHashCode = calculateHashCode();
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
  public void forEachLibrary(@NotNull Consumer<IdeAndroidLibrary> action) {
    for (AndroidLibrary library : myLibraries) {
      action.accept((IdeAndroidLibrary)library);
    }
  }

  @Override
  public void forEachJavaLibrary(@NotNull Consumer<IdeJavaLibrary> action) {
    for (JavaLibrary library : myJavaLibraries) {
      action.accept((IdeJavaLibrary)library);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeDependenciesImpl)) {
      return false;
    }
    IdeDependenciesImpl that = (IdeDependenciesImpl)o;
    return Objects.equals(myLibraries, that.myLibraries) &&
           Objects.equals(myJavaLibraries, that.myJavaLibraries) &&
           Objects.equals(myProjects, that.myProjects);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myLibraries, myJavaLibraries, myProjects);
  }

  @Override
  public String toString() {
    return "IdeDependencies{" +
           "myLibraries=" + myLibraries +
           ", myJavaLibraries=" + myJavaLibraries +
           ", myProjects=" + myProjects +
           '}';
  }
}
