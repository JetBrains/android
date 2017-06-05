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

import com.android.builder.model.AndroidBundle;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class AndroidBundleStub extends LibraryStub implements AndroidBundle {
  @NotNull private final File myBundle;
  @NotNull private final File myFolder;
  @NotNull private final List<AndroidLibrary> myLibraryDependencies;
  @NotNull private final Collection<JavaLibrary> myJavaDependencies;
  @NotNull private final File myManifest;
  @NotNull private final File myJarFile;
  @NotNull private final File myResFolder;
  @NotNull private final File myAssetsFolder;
  @Nullable private final String myProjectVariant;

  public AndroidBundleStub() {
    this(new File("bundle"), new File("folder"), Lists.newArrayList(), Lists.newArrayList(new JavaLibraryStub()),
         new File("manifest"), new File("jarFile"), new File("resFolder"), new File("assetsFolder"), "variant");
  }

  public AndroidBundleStub(@NotNull File bundle,
                           @NotNull File folder,
                           @NotNull List<AndroidLibrary> dependencies,
                           @NotNull Collection<JavaLibrary> javaDependencies,
                           @NotNull File manifest,
                           @NotNull File jarFile,
                           @NotNull File resFolder,
                           @NotNull File assetsFolder,
                           @Nullable String variant) {
    myBundle = bundle;
    myFolder = folder;
    myLibraryDependencies = dependencies;
    myJavaDependencies = javaDependencies;
    myManifest = manifest;
    myJarFile = jarFile;
    myResFolder = resFolder;
    myAssetsFolder = assetsFolder;
    myProjectVariant = variant;
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
  @Nullable
  public String getProjectVariant() {
    return myProjectVariant;
  }

  @Override
  public String toString() {
    return "AndroidBundleStub{" +
           "myBundle=" + myBundle +
           ", myFolder=" + myFolder +
           ", myLibraryDependencies=" + myLibraryDependencies +
           ", myJavaDependencies=" + myJavaDependencies +
           ", myManifest=" + myManifest +
           ", myJarFile=" + myJarFile +
           ", myResFolder=" + myResFolder +
           ", myAssetsFolder=" + myAssetsFolder +
           ", myProjectVariant='" + myProjectVariant + '\'' +
           "} " + super.toString();
  }
}
