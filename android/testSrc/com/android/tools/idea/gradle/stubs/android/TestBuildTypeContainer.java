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

import com.android.ide.common.gradle.model.stubs.BuildTypeContainerStub;
import com.android.ide.common.gradle.model.stubs.BuildTypeStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class TestBuildTypeContainer extends BuildTypeContainerStub {
  TestBuildTypeContainer(@NotNull String name, @NotNull FileStructure fileStructure) {
    super(new BuildTypeStub(name), new TestSourceProvider(fileStructure), Collections.emptyList());
    setUpPaths();
  }
  
  private void setUpPaths() {
    TestSourceProvider sourceProvider = (TestSourceProvider)getSourceProvider();
    String name = getName();
    sourceProvider.addAidlDirectory("src/" + name + "/aidl");
    sourceProvider.addAssetsDirectory("src/" + name + "/assets");
    sourceProvider.addJavaDirectory("src/" + name + "/java");
    sourceProvider.addCDirectory("src/" + name + "/c");
    sourceProvider.addCppDirectory("src/" + name + "/cpp");
    sourceProvider.addRenderscriptDirectory("src/" + name + "/renderscript");
    sourceProvider.addResDirectory("src/" + name + "/rs");
    sourceProvider.addResourcesDirectory("src/" + name + "/resources");
    sourceProvider.setManifestFile("src/" + name + "/manifest.xml");
  }

  @NotNull
  public String getName() {
    return getBuildType().getName();
  }
}
