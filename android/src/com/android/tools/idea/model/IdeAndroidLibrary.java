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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Map;

/**
 * Creates a deep copy of {@link AndroidLibrary}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidLibrary extends IdeAndroidBundle implements AndroidLibrary, Serializable {
  @NotNull private final Collection<File> myLocalJars;
  @NotNull private final File myJniFolder;
  @NotNull private final File myAidlFolder;
  @NotNull private final File myRenderscriptFolder;
  @NotNull private final File myProguardRules;
  @NotNull private final File myLintJar;
  @NotNull private final File myExternalAnnotations;
  @NotNull private final File myPublicResources;
  @NotNull private final File mySymbolFile;
  private final boolean myOptional;

  public IdeAndroidLibrary(@NotNull AndroidLibrary library, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    super(library, seen, gradleVersion);
    myLocalJars = new ArrayList<>(library.getLocalJars());
    myJniFolder = library.getJniFolder();
    myAidlFolder = library.getAidlFolder();
    myRenderscriptFolder = library.getRenderscriptFolder();
    myProguardRules = library.getProguardRules();
    myLintJar = library.getLintJar();
    myExternalAnnotations = library.getExternalAnnotations();
    myPublicResources = library.getPublicResources();
    mySymbolFile = library.getSymbolFile();
    myOptional = library.isOptional();
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
  @Deprecated
  public boolean isOptional() {
    return myOptional;
  }
}
