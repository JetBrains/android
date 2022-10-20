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
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidLibraryStub implements AndroidLibrary {
  @NotNull private final List<File> myLocalJars = new ArrayList<>();

  @NotNull private final File myBundle;
  @NotNull private final File myJarFile;

  @Nullable private final String myProject;
  @Nullable private final String myProjectVariant;
  private MavenCoordinatesStub myResolvedCoordinates;

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
    myResolvedCoordinates = new MavenCoordinatesStub("group", bundle.getName(), "1.0", "jar");
  }

  @Override
  @Nullable
  public String getBuildId() {
    return null;
  }

  @Override
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
    return new File("manifest");
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public File getCompileJarFile() {
    // Use the same jar file for now, we can use a different jar file later
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
  @Nullable
  public File getResStaticLibrary() {
    return new File(getFolder(), "res.apk");
  }

  @Override
  @NotNull
  public File getAssetsFolder() {
    return new File("assets");
  }

  @Override
  @NotNull
  public File getJniFolder() {
    return new File("jni");
  }

  @Override
  @NotNull
  public File getAidlFolder() {
    return new File("aidl");
  }

  @Override
  @NotNull
  public File getRenderscriptFolder() {
    return new File("renderscript");
  }

  @Override
  @NotNull
  public File getProguardRules() {
    return new File("proguardRules");
  }

  @Override
  @NotNull
  public File getLintJar() {
    return new File("lint.jar");
  }

  @Override
  @NotNull
  public File getExternalAnnotations() {
    return new File("externalAnnotations");
  }

  @Override
  @NotNull
  public File getPublicResources() {
    return new File("publicResources");
  }

  @Override
  @NotNull
  public File getSymbolFile() {
    return new File("symbolFile");
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
    return null;
  }

  @Override
  @NotNull
  public MavenCoordinates getResolvedCoordinates() {
    return myResolvedCoordinates;
  }

  @Override
  public boolean isSkipped() {
    return false;
  }
}
