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
 * Creates a deep copy of {@link Library} of type LIBRARY_MODULE.
 */
public final class IdeModuleLibrary extends IdeModel implements Library {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 3L;

  @NotNull private final String myArtifactAddress;
  @Nullable private final String myProjectPath;
  @Nullable private final String myVariant;
  private final int myType;
  private final int myHashCode;

  IdeModuleLibrary(@NotNull Object source,
                   @NotNull String artifactAddress,
                   @NotNull ModelCache modelCache,
                   @Nullable String projectPath,
                   @Nullable String variant) {
    super(source, modelCache);
    myType = LIBRARY_MODULE;
    myArtifactAddress = artifactAddress;
    myProjectPath = projectPath;
    myVariant = variant;
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
    throw unsupportedMethodForModuleLibrary("getArtifact()");
  }

  @Override
  @Nullable
  public String getProjectPath() {
    return myProjectPath;
  }

  @Override
  @Nullable
  public String getVariant() {
    return myVariant;
  }

  @Override
  @NotNull
  public File getFolder() {
    throw unsupportedMethodForModuleLibrary("getFolder");
  }

  @Override
  @NotNull
  public String getManifest() {
    throw unsupportedMethodForModuleLibrary("getManifest");
  }

  @Override
  @NotNull
  public String getJarFile() {
    throw unsupportedMethodForModuleLibrary("getJarFile");
  }

  @Override
  @NotNull
  public String getResFolder() {
    throw unsupportedMethodForModuleLibrary("getResFolder");
  }

  @Override
  @NotNull
  public String getAssetsFolder() {
    throw unsupportedMethodForModuleLibrary("getAssetsFolder");
  }

  @Override
  @NotNull
  public Collection<String> getLocalJars() {
    throw unsupportedMethodForModuleLibrary("getLocalJars");
  }

  @Override
  @NotNull
  public String getJniFolder() {
    throw unsupportedMethodForModuleLibrary("getJniFolder");
  }

  @Override
  @NotNull
  public String getAidlFolder() {
    throw unsupportedMethodForModuleLibrary("getAidlFolder");
  }

  @Override
  @NotNull
  public String getRenderscriptFolder() {
    throw unsupportedMethodForModuleLibrary("getRenderscriptFolder");
  }

  @Override
  @NotNull
  public String getProguardRules() {
    throw unsupportedMethodForModuleLibrary("getProguardRules");
  }

  @Override
  @NotNull
  public String getLintJar() {
    throw unsupportedMethodForModuleLibrary("getLintJar");
  }

  @Override
  @NotNull
  public String getExternalAnnotations() {
    throw unsupportedMethodForModuleLibrary("getExternalAnnotations");
  }

  @Override
  @NotNull
  public String getPublicResources() {
    throw unsupportedMethodForModuleLibrary("getPublicResources");
  }

  @Override
  @NotNull
  public String getSymbolFile() {
    throw unsupportedMethodForModuleLibrary("getSymbolFile");
  }

  @NotNull
  private static UnsupportedOperationException unsupportedMethodForModuleLibrary(@NotNull String methodName) {
    return new UnsupportedOperationException(methodName + "() cannot be called when getType() returns LIBRARY_MODULE");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeModuleLibrary)) {
      return false;
    }
    IdeModuleLibrary that = (IdeModuleLibrary)o;
    return myType == that.myType &&
           Objects.equals(myArtifactAddress, that.myArtifactAddress) &&
           Objects.equals(myProjectPath, that.myProjectPath) &&
           Objects.equals(myVariant, that.myVariant);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myType, myArtifactAddress, myProjectPath, myVariant);
  }

  @Override
  public String toString() {
    return "IdeModuleLibrary{" +
           "myType=" + myType +
           ", myArtifactAddress='" + myArtifactAddress + '\'' +
           ", myProjectPath='" + myProjectPath + '\'' +
           ", myVariant='" + myVariant + '\'' +
           '}';
  }
}