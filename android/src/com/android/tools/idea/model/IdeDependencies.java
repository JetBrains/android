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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Creates a deep copy of {@link Dependencies}.
 *
 * @see IdeAndroidProject
 */
public class IdeDependencies implements Dependencies, Serializable {
  @NotNull private final GradleVersion myModelGradleVersion;
  @NotNull private final Collection<AndroidAtom> myAtoms;
  @NotNull private final Collection<AndroidLibrary> myLibraries;
  @NotNull private final Collection<JavaLibrary> myJavaLibraries;
  @NotNull private final Collection<String> myProjects;
  @Nullable private final AndroidAtom myBaseAtom;

  public IdeDependencies(@NotNull Dependencies dependencies, @NotNull Map<Library, Library> seen, GradleVersion gradleVersion) {
    myModelGradleVersion = gradleVersion;
    myAtoms = new ArrayList<>();
    if (myModelGradleVersion.isAtLeast(2,3,0)) {
      for (AndroidAtom atom : dependencies.getAtoms()) {
        if (!seen.containsKey(atom)) {
          seen.put(atom, new IdeAndroidAtom(atom, seen, gradleVersion));
        }
        myAtoms.add((IdeAndroidAtom)seen.get(atom));
      }
    }

    myLibraries = new ArrayList<>();
    for (AndroidLibrary library : dependencies.getLibraries()) {
      if (!seen.containsKey(library)) {
        seen.put(library, new IdeAndroidLibrary(library, seen, gradleVersion));
      }
      myLibraries.add((IdeAndroidLibrary)seen.get(library));
    }

    myJavaLibraries = new ArrayList<>();
    for (JavaLibrary library : dependencies.getJavaLibraries()) {
      if (!seen.containsKey(library)) {
        seen.put(library, new IdeJavaLibrary(library, seen, gradleVersion));
      }
      myJavaLibraries.add((IdeJavaLibrary)seen.get(library));
    }

    myProjects = new ArrayList<>(dependencies.getProjects());

    if (myModelGradleVersion.isAtLeast(2,3,0)) {
      AndroidAtom deBaseAtom = dependencies.getBaseAtom();
      if (deBaseAtom != null) {
        if (!seen.containsKey(deBaseAtom)) {
          seen.put(deBaseAtom, new IdeAndroidAtom(deBaseAtom, seen, gradleVersion));
        }
        myBaseAtom = (IdeAndroidAtom)seen.get(deBaseAtom);
      }
      else {
        myBaseAtom = null;
      }
    }
    else {
      myBaseAtom = null;
    }
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
  public AndroidAtom getBaseAtom() {
    return myBaseAtom;
  }
}
