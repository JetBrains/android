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
package com.android.tools.idea.model.ide;

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Creates a deep copy of {@link AndroidAtom}.
 *
 * @see IdeAndroidProject
 */
final public class IdeAndroidAtom implements AndroidAtom, Serializable {
  @NotNull private final MavenCoordinates myResolvedCoordinates;
  @NotNull private final File myBundle;
  @NotNull private final File myFolder;
  @NotNull private final List<IdeAndroidLibrary> myLibraryDependencies;
  @NotNull private final Collection<IdeJavaLibrary> myJavaDependencies;
  @NotNull private final File myManifest;
  @NotNull private final File myJarFile;
  @NotNull private final File myResFolder;
  @NotNull private final File myAssetsFolder;
  @NotNull private final String myAtomName;
  @NotNull private final List<IdeAndroidAtom> myAtomDependencies;
  @NotNull private final File myDexFolder;
  @NotNull private final File myLibFolder;
  @NotNull private final File myJavaResFolder;
  @NotNull private final File myResourcePackage;
  @Nullable private final String myProject;
  @Nullable private final String myName;
  @Nullable private final MavenCoordinates myRequestedCoordinates;
  @Nullable private final String myProjectVariant;
  private final boolean mySkipped;
  private final boolean myProvided;

  public IdeAndroidAtom(@NotNull AndroidAtom atom, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    myResolvedCoordinates = new IdeMavenCoordinates(atom.getResolvedCoordinates(), gradleVersion);

    myBundle = atom.getBundle();
    myFolder = atom.getFolder();

    myLibraryDependencies = new ArrayList<>();
    for (AndroidLibrary dependency : atom.getLibraryDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeAndroidLibrary(dependency, seen, gradleVersion));
      }
      myLibraryDependencies.add((IdeAndroidLibrary)seen.get(dependency));
    }

    myJavaDependencies = new ArrayList<>();
    for (JavaLibrary dependency : atom.getJavaDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeJavaLibrary(dependency, seen, gradleVersion));
      }
      myJavaDependencies.add((IdeJavaLibrary)seen.get(dependency));
    }

    myManifest = atom.getManifest();
    myJarFile = atom.getJarFile();
    myResFolder = atom.getResFolder();
    myAssetsFolder = atom.getAssetsFolder();
    myAtomName = atom.getAtomName();

    myAtomDependencies = new ArrayList<>();
    for (AndroidAtom dependency : atom.getAtomDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeAndroidAtom(dependency, seen, gradleVersion));
      }
      myAtomDependencies.add((IdeAndroidAtom)seen.get(dependency));
    }

    myDexFolder = atom.getDexFolder();
    myLibFolder = atom.getLibFolder();
    myJavaResFolder = atom.getJavaResFolder();
    myResourcePackage = atom.getResourcePackage();
    myProject = atom.getProject();
    myName = atom.getName();

    MavenCoordinates liRequestedCoordinate = atom.getRequestedCoordinates();
    myRequestedCoordinates = liRequestedCoordinate == null ? null :new IdeMavenCoordinates(liRequestedCoordinate, gradleVersion);

    myProjectVariant = atom.getProjectVariant();
    mySkipped = atom.isSkipped();

    boolean provided = false;
    try {
      provided = atom.isProvided();
    }
    catch (NullPointerException e) {
      provided = false;
    }
    finally {
      myProvided = provided;
    }
  }

  @Override
  @NotNull
  public MavenCoordinates getResolvedCoordinates() {
    return myResolvedCoordinates;
  }

  @Override
  @NotNull
  public File getBundle() {
    return myBundle;
  }

  @Override
  @NotNull
  public File getFolder() {
    return myFolder;
  }

  @Override
  @NotNull
  public List<? extends AndroidLibrary> getLibraryDependencies() {
    return myLibraryDependencies;
  }

  @Override
  @NotNull
  public Collection<? extends JavaLibrary> getJavaDependencies() {
    return myJavaDependencies;
  }

  @Override
  @NotNull
  public File getManifest() {
    return myManifest;
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public File getResFolder() {
    return myResFolder;
  }

  @Override
  @NotNull
  public File getAssetsFolder() {
    return myAssetsFolder;
  }

  @Override
  @NotNull
  public String getAtomName() {
    return myAtomName;
  }

  @Override
  @NotNull
  public List<? extends AndroidAtom> getAtomDependencies() {
    return myAtomDependencies;
  }

  @Override
  @NotNull
  public File getDexFolder() {
    return myDexFolder;
  }

  @Override
  @NotNull
  public File getLibFolder() {
    return myLibFolder;
  }

  @Override
  @NotNull
  public File getJavaResFolder() {
    return myJavaResFolder;
  }

  @Override
  @NotNull
  public File getResourcePackage() {
    return myResourcePackage;
  }
  @Override
  @Nullable
  public String getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public MavenCoordinates getRequestedCoordinates() {
    return myRequestedCoordinates;
  }

  @Override
  @Nullable
  public String getProjectVariant() {
    return myProjectVariant;
  }

  @Override
  public boolean isSkipped() {
    return mySkipped;
  }

  @Override
  public boolean isProvided() {
    return myProvided;
  }
}
