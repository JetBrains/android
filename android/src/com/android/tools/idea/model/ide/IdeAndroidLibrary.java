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
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Creates a deep copy of {@link AndroidLibrary}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidLibrary implements AndroidLibrary, Serializable {
  @NotNull private final MavenCoordinates myResolvedCoordinates;
  @NotNull private final File myBundle;
  @NotNull private final File myFolder;
  @NotNull private final List<IdeAndroidLibrary> myLibraryDependencies;
  @NotNull private final Collection<IdeJavaLibrary> myJavaDependencies;
  @NotNull private final File myManifest;
  @NotNull private final File myJarFile;
  @NotNull private final File myResFolder;
  @NotNull private final File myAssetsFolder;
  @NotNull private final Collection<File> myLocalJars;
  @NotNull private final File myJniFolder;
  @NotNull private final File myAidlFolder;
  @NotNull private final File myRenderscriptFolder;
  @NotNull private final File myProguardRules;
  @NotNull private final File myLintJar;
  @NotNull private final File myExternalAnnotations;
  @NotNull private final File myPublicResources;
  @NotNull private final File mySymbolFile;
  @Nullable private final String myProject;
  @Nullable private final String myName;
  @Nullable private final MavenCoordinates myRequestedCoordinates;
  @Nullable private final String myProjectVariant;
  private final boolean mySkipped;
  private final boolean myProvided;
  private final boolean myOptional;

  public IdeAndroidLibrary(@NotNull AndroidLibrary library, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    myResolvedCoordinates = new IdeMavenCoordinates(library.getResolvedCoordinates(), gradleVersion);

    myBundle = library.getBundle();
    myFolder = library.getFolder();

    myLibraryDependencies = new ArrayList<>();
    for (AndroidLibrary dependency : library.getLibraryDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeAndroidLibrary(dependency, seen, gradleVersion));
      }
      myLibraryDependencies.add((IdeAndroidLibrary)seen.get(dependency));
    }

    myJavaDependencies = new ArrayList<>();
    for (JavaLibrary dependency : library.getJavaDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeJavaLibrary(dependency, seen, gradleVersion));
      }
      myJavaDependencies.add((IdeJavaLibrary)seen.get(dependency));
    }

    myManifest = library.getManifest();
    myJarFile = library.getJarFile();
    myResFolder = library.getResFolder();
    myAssetsFolder = library.getAssetsFolder();
    myLocalJars = new ArrayList<>(library.getLocalJars());
    myJniFolder = library.getJniFolder();
    myAidlFolder = library.getAidlFolder();
    myRenderscriptFolder = library.getRenderscriptFolder();
    myProguardRules = library.getProguardRules();
    myLintJar = library.getLintJar();
    myExternalAnnotations = library.getExternalAnnotations();
    myPublicResources = library.getPublicResources();
    mySymbolFile = library.getSymbolFile();
    myProject = library.getProject();
    myName = library.getName();

    MavenCoordinates liRequestedCoordinate = library.getRequestedCoordinates();
    myRequestedCoordinates = liRequestedCoordinate == null ? null :new IdeMavenCoordinates(liRequestedCoordinate, gradleVersion);

    myProjectVariant = library.getProjectVariant();
    mySkipped = library.isSkipped();

    boolean provided = false;
    try {
      provided = library.isProvided();
    }
    catch (NullPointerException e) {
      provided = false;
    }
    finally {
      myProvided = provided;
    }

    boolean optional = false;
    try {
      // isOptional is deprecated and isProvided should be used instead when null is returned
      optional = library.isOptional();
    }
    catch (NullPointerException e) {
      optional = !library.isProvided();
    }
    finally {
      myOptional = optional;
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
  public Collection<File> getLocalJars() {
    return myLocalJars;
  }

  @Override
  @NotNull
  public File getJniFolder() {
    return myJniFolder;
  }

  @Override
  @NotNull
  public File getAidlFolder() {
    return myAidlFolder;
  }

  @Override
  @NotNull
  public File getRenderscriptFolder() {
    return myRenderscriptFolder;
  }

  @Override
  @NotNull
  public File getProguardRules() {
    return myProguardRules;
  }

  @Override
  @NotNull
  public File getLintJar() {
    return myLintJar;
  }

  @Override
  @NotNull
  public File getExternalAnnotations() {
    return myExternalAnnotations;
  }

  @Override
  @NotNull
  public File getPublicResources() {
    return myPublicResources;
  }

  @Override
  @NotNull
  public File getSymbolFile() {
    return mySymbolFile;
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

  @Override
  @Deprecated
  public boolean isOptional() {
    return myOptional;
  }
}
