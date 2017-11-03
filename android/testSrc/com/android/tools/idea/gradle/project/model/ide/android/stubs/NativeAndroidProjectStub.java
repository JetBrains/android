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

import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class NativeAndroidProjectStub extends BaseStub implements NativeAndroidProject {
  @NotNull private final String myModelVersion;
  @NotNull private final String myName;
  @NotNull private final List<File> myBuildFiles;
  @NotNull private final Collection<NativeArtifact> myArtifacts;
  @NotNull private final Collection<NativeToolchain> myToolChains;
  @NotNull private final Collection<NativeSettings> mySettings;
  @NotNull private final Map<String, String> myFileExtensions;
  @NotNull private final Collection<String> myBuildSystems;
  private final int myApiVersion;

  public NativeAndroidProjectStub() {
    this("1.0", "name", Collections.singletonList(new File("buildFile")), Collections.singletonList(new NativeArtifactStub()),
         Collections.singletonList(new NativeToolchainStub()), Collections.singletonList(new NativeSettingsStub()),
         ImmutableMap.<String, String>builder().put("key", "value").build(), Collections.singletonList("buildSystem"), 1);
  }

  public NativeAndroidProjectStub(@NotNull String modelVersion,
                                  @NotNull String name,
                                  @NotNull List<File> buildFiles,
                                  @NotNull Collection<NativeArtifact> artifacts,
                                  @NotNull Collection<NativeToolchain> toolChains,
                                  @NotNull Collection<NativeSettings> settings,
                                  @NotNull Map<String, String> fileExtensions,
                                  @NotNull Collection<String> buildSystems,
                                  int apiVersion) {
    myModelVersion = modelVersion;
    myName = name;
    myBuildFiles = buildFiles;
    myArtifacts = artifacts;
    myToolChains = toolChains;
    mySettings = settings;
    myFileExtensions = fileExtensions;
    myBuildSystems = buildSystems;
    myApiVersion = apiVersion;
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
    return myBuildSystems;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NativeAndroidProject)) {
      return false;
    }
    NativeAndroidProject project = (NativeAndroidProject)o;
    return getApiVersion() == project.getApiVersion() &&
           Objects.equals(getModelVersion(), project.getModelVersion()) &&
           Objects.equals(getName(), project.getName()) &&
           Objects.equals(getBuildFiles(), project.getBuildFiles()) &&
           Objects.equals(getArtifacts(), project.getArtifacts()) &&
           Objects.equals(getToolChains(), project.getToolChains()) &&
           Objects.equals(getSettings(), project.getSettings()) &&
           Objects.equals(getFileExtensions(), project.getFileExtensions()) &&
           Objects.equals(getBuildSystems(), project.getBuildSystems());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getModelVersion(), getName(), getBuildFiles(), getArtifacts(), getToolChains(), getSettings(), getFileExtensions(),
                        getBuildSystems(), getApiVersion());
  }
}
