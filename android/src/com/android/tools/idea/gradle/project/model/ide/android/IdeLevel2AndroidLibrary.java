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

import com.android.builder.model.level2.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

/**
 * Creates a deep copy of {@link Library} if getType() returns LIBRARY_ANDROID
 */
public final class IdeLevel2AndroidLibrary extends IdeModel implements Library {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myArtifactAddress;
  @NotNull private final File myFolder;
  @NotNull private final String myManifest;
  @NotNull private final String myJarFile;
  @NotNull private final String myResFolder;
  @NotNull private final String myAssetsFolder;
  @NotNull private final Collection<String> myLocalJars;
  @NotNull private final String myJniFolder;
  @NotNull private final String myAidlFolder;
  @NotNull private final String myRenderscriptFolder;
  @NotNull private final String myProguardRules;
  @NotNull private final String myLintJar;
  @NotNull private final String myExternalAnnotations;
  @NotNull private final String myPublicResources;
  @NotNull private final String mySymbolFile;
  @Nullable private final File myArtifactFile;
  private final int myType;
  private final int myHashCode;

  public IdeLevel2AndroidLibrary(@NotNull Library library, @NotNull ModelCache modelCache) {
    super(library, modelCache);

    myType = library.getType();
    myArtifactAddress = library.getArtifactAddress();
    myFolder = library.getFolder();
    myManifest = library.getManifest();
    myJarFile = library.getJarFile();
    myResFolder = library.getResFolder();
    myAssetsFolder = library.getAssetsFolder();
    myLocalJars = library.getLocalJars();
    myJniFolder = library.getJniFolder();
    myAidlFolder = library.getAidlFolder();
    myRenderscriptFolder = library.getRenderscriptFolder();
    myProguardRules = library.getProguardRules();
    myLintJar = library.getLintJar();
    myExternalAnnotations = library.getExternalAnnotations();
    myPublicResources = library.getPublicResources();
    mySymbolFile = library.getSymbolFile();
    File artifactFile;
    try {
      // getArtifact throws exception if the value is null.
      artifactFile = library.getArtifact();
    }
    catch (UnsupportedOperationException e) {
      artifactFile = null;
    }
    myArtifactFile = artifactFile;
    myHashCode = calculateHashCode();
  }

  @Override
  public int getType() {
    return myType;
  }

  @Override
  @NotNull
  public String getArtifactAddress() {
    return myArtifactAddress;
  }

  @Override
  @NotNull
  public File getArtifact() {
    if (myArtifactFile == null) {
      throw new UnsupportedOperationException(
        "getArtifact() cannot be called when getType() returns ANDROID_LIBRARY");
    }
    return myArtifactFile;
  }

  @Override
  @NotNull
  public File getFolder() {
    return myFolder;
  }

  @Override
  @NotNull
  public String getManifest() {
    return myManifest;
  }

  @Override
  @NotNull
  public String getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public String getResFolder() {
    return myResFolder;
  }

  @Override
  @NotNull
  public String getAssetsFolder() {
    return myAssetsFolder;
  }

  @Override
  @NotNull
  public Collection<String> getLocalJars() {
    return myLocalJars;
  }

  @Override
  @NotNull
  public String getJniFolder() {
    return myJniFolder;
  }

  @Override
  @NotNull
  public String getAidlFolder() {
    return myAidlFolder;
  }

  @Override
  @NotNull
  public String getRenderscriptFolder() {
    return myRenderscriptFolder;
  }

  @Override
  @NotNull
  public String getProguardRules() {
    return myProguardRules;
  }

  @Override
  @NotNull
  public String getLintJar() {
    return myLintJar;
  }

  @Override
  @NotNull
  public String getExternalAnnotations() {
    return myExternalAnnotations;
  }

  @Override
  @NotNull
  public String getPublicResources() {
    return myPublicResources;
  }

  @Override
  @NotNull
  public String getSymbolFile() {
    return mySymbolFile;
  }

  @Override
  @Nullable
  public String getVariant() {
    throw new UnsupportedOperationException(
      "getVariant() cannot be called when getType() returns ANDROID_LIBRARY");
  }

  @Override
  @NotNull
  public String getProjectPath() {
    throw new UnsupportedOperationException(
      "getProjectPath() cannot be called when getType() returns ANDROID_LIBRARY");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeLevel2AndroidLibrary)) {
      return false;
    }
    IdeLevel2AndroidLibrary that = (IdeLevel2AndroidLibrary)o;
    return myType == that.myType &&
           Objects.equals(myArtifactAddress, that.myArtifactAddress) &&
           Objects.equals(myFolder, that.myFolder) &&
           Objects.equals(myManifest, that.myManifest) &&
           Objects.equals(myJarFile, that.myJarFile) &&
           Objects.equals(myResFolder, that.myResFolder) &&
           Objects.equals(myAssetsFolder, that.myAssetsFolder) &&
           Objects.equals(myLocalJars, that.myLocalJars) &&
           Objects.equals(myJniFolder, that.myJniFolder) &&
           Objects.equals(myAidlFolder, that.myAidlFolder) &&
           Objects.equals(myRenderscriptFolder, that.myRenderscriptFolder) &&
           Objects.equals(myProguardRules, that.myProguardRules) &&
           Objects.equals(myLintJar, that.myLintJar) &&
           Objects.equals(myExternalAnnotations, that.myExternalAnnotations) &&
           Objects.equals(myPublicResources, that.myPublicResources) &&
           Objects.equals(mySymbolFile, that.mySymbolFile) &&
           Objects.equals(myArtifactFile, that.myArtifactFile);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects
      .hash(myType, myArtifactAddress, myFolder, myManifest, myJarFile, myResFolder, myAssetsFolder, myLocalJars, myJniFolder, myAidlFolder,
            myRenderscriptFolder, myProguardRules, myLintJar, myExternalAnnotations, myPublicResources, mySymbolFile, myArtifactFile);
  }

  @Override
  public String toString() {
    return "IdeLevel2AndroidLibrary{" +
           "myType=" + myType +
           ", myArtifactAddress='" + myArtifactAddress + '\'' +
           ", myFolder=" + myFolder +
           ", myManifest='" + myManifest + '\'' +
           ", myJarFile='" + myJarFile + '\'' +
           ", myResFolder='" + myResFolder + '\'' +
           ", myAssetsFolder='" + myAssetsFolder + '\'' +
           ", myLocalJars=" + myLocalJars +
           ", myJniFolder='" + myJniFolder + '\'' +
           ", myAidlFolder='" + myAidlFolder + '\'' +
           ", myRenderscriptFolder='" + myRenderscriptFolder + '\'' +
           ", myProguardRules='" + myProguardRules + '\'' +
           ", myLintJar='" + myLintJar + '\'' +
           ", myExternalAnnotations='" + myExternalAnnotations + '\'' +
           ", myPublicResources='" + myPublicResources + '\'' +
           ", mySymbolFile='" + mySymbolFile + '\'' +
           ", myArtifactFile=" + myArtifactFile +
           '}';
  }
}