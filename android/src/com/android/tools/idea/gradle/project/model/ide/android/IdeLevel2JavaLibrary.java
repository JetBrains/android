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
 * Creates a deep copy of {@link Library} if getType() returns LIBRARY_JAVA
 */
public final class IdeLevel2JavaLibrary extends IdeModel implements Library {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myArtifactAddress;
  @NotNull private final File myArtifactFile;
  private final int myType;
  private final int myHashCode;

  public IdeLevel2JavaLibrary(@NotNull Library library, @NotNull ModelCache modelCache) {
    super(library, modelCache);
    myType = library.getType();
    myArtifactAddress = library.getArtifactAddress();
    myArtifactFile = library.getArtifact();
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
    return myArtifactFile;
  }

  @Override
  @Nullable
  public String getVariant() {
    throw new UnsupportedOperationException(
      "getVariant() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getProjectPath() {
    throw new UnsupportedOperationException(
      "getProjectPath() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public File getFolder() {
    throw new UnsupportedOperationException(
      "getFolder() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getManifest() {
    throw new UnsupportedOperationException(
      "getManifest() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getJarFile() {
    throw new UnsupportedOperationException(
      "getJarFile() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getResFolder() {
    throw new UnsupportedOperationException(
      "getResFolder() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getAssetsFolder() {
    throw new UnsupportedOperationException(
      "getAssetsFolder() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public Collection<String> getLocalJars() {
    throw new UnsupportedOperationException(
      "getLocalJars() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getJniFolder() {
    throw new UnsupportedOperationException(
      "getJniFolder() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getAidlFolder() {
    throw new UnsupportedOperationException(
      "getAidlFolder() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getRenderscriptFolder() {
    throw new UnsupportedOperationException(
      "getRenderscriptFolder() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getProguardRules() {
    throw new UnsupportedOperationException(
      "getProguardRules() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getLintJar() {
    throw new UnsupportedOperationException(
      "getLintJar() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getExternalAnnotations() {
    throw new UnsupportedOperationException(
      "getExternalAnnotations() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getPublicResources() {
    throw new UnsupportedOperationException(
      "getPublicResources() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  @NotNull
  public String getSymbolFile() {
    throw new UnsupportedOperationException(
      "getSymbolFile() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeLevel2JavaLibrary)) {
      return false;
    }
    IdeLevel2JavaLibrary that = (IdeLevel2JavaLibrary)o;
    return myType == that.myType &&
           Objects.equals(myArtifactAddress, that.myArtifactAddress) &&
           Objects.equals(myArtifactFile, that.myArtifactFile);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myType, myArtifactAddress, myArtifactFile);
  }

  @Override
  public String toString() {
    return "IdeLevel2JavaLibrary{" +
           "myType=" + myType +
           ", myArtifactAddress='" + myArtifactAddress + '\'' +
           ", myArtifactFile=" + myArtifactFile +
           '}';
  }
}
