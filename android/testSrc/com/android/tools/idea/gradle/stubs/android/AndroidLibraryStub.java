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

import com.android.SdkConstants;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidLibraryStub implements AndroidLibrary {
  @NotNull private final List<File> myLocalJars = Lists.newArrayList();

  @NotNull private final File myBundle;
  @NotNull private final File myJarFile;

  @Nullable private final String myProject;
  @Nullable private final String myProjectVariant;


  public AndroidLibraryStub(@NotNull File bundle, @NotNull File jarFile) {
    this(bundle, jarFile, null);
  }

  public AndroidLibraryStub(@NotNull File bundle, @NotNull File jarFile, @Nullable String project) {
    this(bundle, jarFile, project, null);
  }

  public AndroidLibraryStub(@NotNull File bundle, @NotNull File jarFile, @Nullable String project, @Nullable String projectVariant) {
    myBundle = bundle;
    myJarFile = jarFile;
    myProject = project;
    myProjectVariant = projectVariant;
  }

  @Override
  @Nullable
  public String getProject() {
    return myProject;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  @Nullable
  public String getProjectVariant() {
    return myProjectVariant;
  }

  @Override
  @NotNull
  public File getBundle() {
    return myBundle;
  }

  @Override
  @NotNull
  public File getFolder() {
    return myJarFile.getParentFile();
  }

  @Override
  @NotNull
  public List<? extends AndroidLibrary> getLibraryDependencies() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public File getManifest() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  public void addLocalJar(@NotNull File localJar) {
    myLocalJars.add(localJar);
  }

  @Override
  @NotNull
  public List<File> getLocalJars() {
    return myLocalJars;
  }

  @Override
  @NotNull
  public Collection<? extends JavaLibrary> getJavaDependencies() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public File getResFolder() {
    return new File(getFolder(), SdkConstants.FD_RES);
  }

  @Override
  @NotNull
  public File getAssetsFolder() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getJniFolder() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getAidlFolder() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getRenderscriptFolder() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getProguardRules() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getLintJar() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getExternalAnnotations() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getPublicResources() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public File getSymbolFile() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isProvided() {
    return false;
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @Override
  @Nullable
  public MavenCoordinates getRequestedCoordinates() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public MavenCoordinates getResolvedCoordinates() {
    return null;
  }

  @Override
  public boolean isSkipped() {
    return false;
  }
}
