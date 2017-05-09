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

import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link Dependencies}.
 */
public final class IdeDependencies extends IdeModel implements Dependencies {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;
  private final int myHashCode;

  @NotNull private final Collection<AndroidAtom> myAtoms;
  @NotNull private final Collection<AndroidLibrary> myLibraries;
  @NotNull private final Collection<JavaLibrary> myJavaLibraries;
  @NotNull private final Collection<String> myProjects;
  @Nullable private final IdeAndroidAtom myBaseAtom;

  public IdeDependencies(@NotNull Dependencies dependencies, @NotNull ModelCache modelCache, @NotNull GradleVersion gradleVersion) {
    super(dependencies, modelCache);
    boolean atLeastTwoDotThree = gradleVersion.isAtLeast(2, 3, 0);
    if (atLeastTwoDotThree) {
      myAtoms = copy(dependencies.getAtoms(), modelCache, atom -> new IdeAndroidAtom(atom, modelCache));
    }
    else {
      myAtoms = Collections.emptyList();
    }

    myLibraries = copy(dependencies.getLibraries(), modelCache, library -> new IdeAndroidLibrary(library, modelCache));
    myJavaLibraries = copy(dependencies.getJavaLibraries(), modelCache, library -> new IdeJavaLibrary(library, modelCache));

    //noinspection deprecation
    myProjects = new ArrayList<>(dependencies.getProjects());

    AndroidAtom baseAtom = atLeastTwoDotThree ? dependencies.getBaseAtom() : null;
    myBaseAtom = copyAtom(modelCache, baseAtom);

    myHashCode = calculateHashCode();
  }

  @Nullable
  private static IdeAndroidAtom copyAtom(@NotNull ModelCache modelCache, @Nullable AndroidAtom atom) {
    if (atom != null) {
      return modelCache.computeIfAbsent(atom, library -> new IdeAndroidAtom(atom, modelCache));
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<AndroidAtom> getAtoms() {
    return myAtoms;
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
  @Nullable
  public IdeAndroidAtom getBaseAtom() {
    return myBaseAtom;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeDependencies)) {
      return false;
    }
    IdeDependencies that = (IdeDependencies)o;
    return Objects.equals(myAtoms, that.myAtoms) &&
           Objects.equals(myLibraries, that.myLibraries) &&
           Objects.equals(myJavaLibraries, that.myJavaLibraries) &&
           Objects.equals(myProjects, that.myProjects) &&
           Objects.equals(myBaseAtom, that.myBaseAtom);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  protected int calculateHashCode() {
    return Objects.hash(myAtoms, myLibraries, myJavaLibraries, myProjects, myBaseAtom);
  }

  @Override
  public String toString() {
    return "IdeDependencies{" +
           "myAtoms=" + myAtoms +
           ", myLibraries=" + myLibraries +
           ", myJavaLibraries=" + myJavaLibraries +
           ", myProjects=" + myProjects +
           ", myBaseAtom=" + myBaseAtom +
           '}';
  }
}
