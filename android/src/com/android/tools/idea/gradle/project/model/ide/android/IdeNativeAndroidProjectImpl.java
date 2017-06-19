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

import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public final class IdeNativeAndroidProjectImpl extends IdeModel implements IdeNativeAndroidProject {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myModelVersion;
  @NotNull private final String myName;
  @NotNull private final List<File> myBuildFiles;
  @NotNull private final Collection<NativeArtifact> myArtifacts;
  @NotNull private final Collection<NativeToolchain> myToolChains;
  @NotNull private final Collection<NativeSettings> mySettings;
  @NotNull private final Map<String, String> myFileExtensions;
  @Nullable private final Collection<String> myBuildSystems;
  private final int myApiVersion;
  private final int myHashCode;

  public IdeNativeAndroidProjectImpl(@NotNull NativeAndroidProject project) {
    this(project, new ModelCache());
  }

  @VisibleForTesting
  IdeNativeAndroidProjectImpl(@NotNull NativeAndroidProject project, @NotNull ModelCache modelCache) {
    super(project, modelCache);
    myModelVersion = project.getModelVersion();
    myApiVersion = project.getApiVersion();
    myName = project.getName();
    myBuildFiles = ImmutableList.copyOf(project.getBuildFiles());
    myArtifacts = copy(project.getArtifacts(), modelCache, artifact -> new IdeNativeArtifact(artifact, modelCache));
    myToolChains = copy(project.getToolChains(), modelCache, toolchain -> new IdeNativeToolchain(toolchain, modelCache));
    mySettings = copy(project.getSettings(), modelCache, settings -> new IdeNativeSettings(settings, modelCache));
    myFileExtensions = ImmutableMap.copyOf(project.getFileExtensions());
    myBuildSystems = copyBuildSystems(project);
    myHashCode = calculateHashCode();
  }

  @Nullable
  private static Collection<String> copyBuildSystems(@NotNull NativeAndroidProject project) {
    try {
      return ImmutableList.copyOf(project.getBuildSystems());
    }
    catch (UnsupportedMethodException e) {
      return null;
    }
  }

  @Override
  @NotNull
  public String getModelVersion() {
    return myModelVersion;
  }

  @Override
  public int getApiVersion() {
    return myApiVersion;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Collection<File> getBuildFiles() {
    return myBuildFiles;
  }

  @Override
  @NotNull
  public Collection<NativeArtifact> getArtifacts() {
    return myArtifacts;
  }

  @Override
  @NotNull
  public Collection<NativeToolchain> getToolChains() {
    return myToolChains;
  }

  @Override
  @NotNull
  public Collection<NativeSettings> getSettings() {
    return mySettings;
  }

  @Override
  @NotNull
  public Map<String, String> getFileExtensions() {
    return myFileExtensions;
  }

  @Override
  @NotNull
  public Collection<String> getBuildSystems() {
    if (myBuildSystems != null) {
      return myBuildSystems;
    }
    throw new UnsupportedMethodException("Unsupported method: NativeAndroidProject.getBuildSystems()");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeNativeAndroidProjectImpl)) {
      return false;
    }
    IdeNativeAndroidProjectImpl project = (IdeNativeAndroidProjectImpl)o;
    return myApiVersion == project.myApiVersion &&
           Objects.equals(myModelVersion, project.myModelVersion) &&
           Objects.equals(myName, project.myName) &&
           Objects.equals(myBuildFiles, project.myBuildFiles) &&
           Objects.equals(myArtifacts, project.myArtifacts) &&
           Objects.equals(myToolChains, project.myToolChains) &&
           Objects.equals(mySettings, project.mySettings) &&
           Objects.equals(myFileExtensions, project.myFileExtensions) &&
           Objects.equals(myBuildSystems, project.myBuildSystems);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myModelVersion, myName, myBuildFiles, myArtifacts, myToolChains, mySettings, myFileExtensions, myBuildSystems,
                        myApiVersion);
  }

  @Override
  public String toString() {
    return "IdeNativeAndroidProject{" +
           "myModelVersion='" + myModelVersion + '\'' +
           ", myName='" + myName + '\'' +
           ", myBuildFiles=" + myBuildFiles +
           ", myArtifacts=" + myArtifacts +
           ", myToolChains=" + myToolChains +
           ", mySettings=" + mySettings +
           ", myFileExtensions=" + myFileExtensions +
           ", myBuildSystems=" + myBuildSystems +
           ", myApiVersion=" + myApiVersion +
           "}";
  }

  public static class FactoryImpl implements Factory {
    @Override
    @NotNull
    public IdeNativeAndroidProject create(@NotNull NativeAndroidProject project) {
      return new IdeNativeAndroidProjectImpl(project);
    }
  }
}
