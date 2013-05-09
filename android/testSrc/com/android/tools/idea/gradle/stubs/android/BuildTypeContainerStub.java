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

import com.android.build.gradle.model.BuildTypeContainer;
import com.android.build.gradle.model.Dependencies;
import com.android.builder.model.BuildType;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.FileStructure;
import org.jetbrains.annotations.NotNull;

public class BuildTypeContainerStub implements BuildTypeContainer {
  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final SourceProviderStub mySourceProvider;

  BuildTypeContainerStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    myName = name;
    myFileStructure = fileStructure;
    mySourceProvider = new SourceProviderStub(fileStructure);
    setUpPaths();
  }
  
  private void setUpPaths() {
    mySourceProvider.addAidlDirectory("src/" + myName + "/aidl");
    mySourceProvider.addAssetsDirectory("src/" + myName + "/assets");
    mySourceProvider.addJavaDirectory("src/" + myName + "/java");
    mySourceProvider.addJniDirectory("src/" + myName + "/jni");
    mySourceProvider.addRenderscriptDirectory("src/" + myName + "/renderscript");
    mySourceProvider.addResDirectory("src/" + myName + "/rs");
    mySourceProvider.addResourcesDirectory("src/" + myName + "/resources");
    mySourceProvider.setManifestFile("src/" + myName + "/manifest.xml");

  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public BuildType getBuildType() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  @NotNull
  public Dependencies getDependency() {
    throw new UnsupportedOperationException();
  }
}
