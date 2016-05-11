/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NativeAndroidProjectStub implements NativeAndroidProject {
  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final List<NativeArtifact> myNativeArtifacts = Lists.newArrayList();

  @NotNull private String myModelVersion = SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION + "-SNAPSHOT";

  public NativeAndroidProjectStub(@NotNull String name) {
    this(name, new FileStructure(name));
  }

  public NativeAndroidProjectStub(@NotNull File parentDir, @NotNull String name) {
    this(name, new FileStructure(parentDir, name));
  }

  private NativeAndroidProjectStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    myName = name;
    myFileStructure = fileStructure;
  }

  @NotNull
  @Override
  public String getModelVersion() {
    return myModelVersion;
  }

  public void setModelVersion(@NotNull String modelVersion) {
    myModelVersion = modelVersion;
  }

  @Override
  public int getApiVersion() {
    return 3;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public Collection<File> getBuildFiles() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  public Collection<NativeArtifact> getArtifacts() {
    return myNativeArtifacts;
  }

  public NativeArtifactStub addNativeArtifact(@NotNull String artifactName) {
    NativeArtifactStub artifact = new NativeArtifactStub(artifactName, new File(artifactName + ".so"), myFileStructure);
    myNativeArtifacts.add(artifact);
    return artifact;
  }

  @NotNull
  @Override
  public Collection<NativeToolchain> getToolChains() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  public Collection<NativeSettings> getSettings() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  public Map<String, String> getFileExtensions() {
    return ImmutableMap.of();
  }

  @NotNull
  @Override
  public Collection<String> getBuildSystems() {
    return ImmutableList.of();
  }

  /**
   * Deletes this project's directory structure.
   *
  public void dispose() {
    myFileStructure.dispose();
  }

  /**
   * @return this project's root directory.
   */
  @NotNull
  public File getRootDir() {
    return myFileStructure.getRootDir();
  }
}
