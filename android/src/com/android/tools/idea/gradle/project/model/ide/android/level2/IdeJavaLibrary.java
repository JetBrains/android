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
package com.android.tools.idea.gradle.project.model.ide.android.level2;


import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.model.ide.android.IdeModel;
import com.android.tools.idea.gradle.project.model.ide.android.ModelCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

/**
 * Creates a deep copy of {@link Library} of type LIBRARY_JAVA.
 */
public final class IdeJavaLibrary extends IdeModel implements Library {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private final String myArtifactAddress;
  @NotNull private final File myArtifactFile;
  private final int myType;
  private final int myHashCode;

  IdeJavaLibrary(@NotNull String artifactAddress,
                 @NotNull File artifactFile,
                 @NotNull ModelCache modelCache,
                 @NotNull Object sourceObject) {
    super(sourceObject, modelCache);
    myType = LIBRARY_JAVA;
    myArtifactAddress = artifactAddress;
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
    return myArtifactFile;
  }

  @Override
  @Nullable
  public String getVariant() {
    throw unsupportedMethodForJavaLibrary("getVariant");
  }

  @Override
  @NotNull
  public String getProjectPath() {
    throw unsupportedMethodForJavaLibrary("getProjectPath");
  }

  @Override
  @NotNull
  public File getFolder() {
    throw unsupportedMethodForJavaLibrary("getFolder");
  }

  @Override
  @NotNull
  public String getManifest() {
    throw unsupportedMethodForJavaLibrary("getManifest");
  }

  @Override
  @NotNull
  public String getJarFile() {
    throw unsupportedMethodForJavaLibrary("getJarFile");
  }

  @Override
  @NotNull
  public String getResFolder() {
    throw unsupportedMethodForJavaLibrary("getResFolder");
  }

  @Override
  @NotNull
  public String getAssetsFolder() {
    throw unsupportedMethodForJavaLibrary("getAssetsFolder");
  }

  @Override
  @NotNull
  public Collection<String> getLocalJars() {
    throw unsupportedMethodForJavaLibrary("getLocalJars");
  }

  @Override
  @NotNull
  public String getJniFolder() {
    throw unsupportedMethodForJavaLibrary("getJniFolder");
  }

  @Override
  @NotNull
  public String getAidlFolder() {
    throw unsupportedMethodForJavaLibrary("getAidlFolder");
  }

  @Override
  @NotNull
  public String getRenderscriptFolder() {
    throw unsupportedMethodForJavaLibrary("getRenderscriptFolder");
  }

  @Override
  @NotNull
  public String getProguardRules() {
    throw unsupportedMethodForJavaLibrary("getProguardRules");
  }

  @Override
  @NotNull
  public String getLintJar() {
    throw unsupportedMethodForJavaLibrary("getLintJar");
  }

  @Override
  @NotNull
  public String getExternalAnnotations() {
    throw unsupportedMethodForJavaLibrary("getExternalAnnotations");
  }

  @Override
  @NotNull
  public String getPublicResources() {
    throw unsupportedMethodForJavaLibrary( "getPublicResources");
  }

  @Override
  @NotNull
  public String getSymbolFile() {
    throw unsupportedMethodForJavaLibrary("getSymbolFile");
  }

  @NotNull
  private static UnsupportedOperationException unsupportedMethodForJavaLibrary(@NotNull String methodName) {
    return new UnsupportedOperationException(methodName + "() cannot be called when getType() returns LIBRARY_JAVA");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeJavaLibrary)) {
      return false;
    }
    IdeJavaLibrary that = (IdeJavaLibrary)o;
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
    return "IdeJavaLibrary{" +
           "myType=" + myType +
           ", myArtifactAddress='" + myArtifactAddress + '\'' +
           ", myArtifactFile=" + myArtifactFile +
           '}';
  }
}
