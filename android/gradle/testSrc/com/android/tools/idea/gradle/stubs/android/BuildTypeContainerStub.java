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
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProviderContainer;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class BuildTypeContainerStub implements BuildTypeContainer {
  @NotNull private final String myName;
  @NotNull private final SourceProviderStub mySourceProvider;
  private final BuildTypeStub myBuildType;

  public BuildTypeContainerStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    myName = name;
    myBuildType = new BuildTypeStub(name);
    mySourceProvider = new SourceProviderStub(fileStructure, "src/" + myName + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    setUpPaths();
  }
  
  private void setUpPaths() {
    mySourceProvider.addAidlDirectory("src/" + myName + "/aidl");
    mySourceProvider.addAssetsDirectory("src/" + myName + "/assets");
    mySourceProvider.addJavaDirectory("src/" + myName + "/java");
    mySourceProvider.addCDirectory("src/" + myName + "/c");
    mySourceProvider.addCppDirectory("src/" + myName + "/cpp");
    mySourceProvider.addRenderscriptDirectory("src/" + myName + "/renderscript");
    mySourceProvider.addBaselineProfileDirectory("src/" + myName + "/baselineProfiles");
    mySourceProvider.addResDirectory("src/" + myName + "/rs");
    mySourceProvider.addResourcesDirectory("src/" + myName + "/resources");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public BuildTypeStub getBuildType() {
    return myBuildType;
  }

  @Override
  @NotNull
  public SourceProviderStub getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  @NotNull
  public Collection<SourceProviderContainer> getExtraSourceProviders() {
    return ImmutableSet.of();
  }
}
