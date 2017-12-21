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

import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub;
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub;
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class TestProductFlavorContainer extends ProductFlavorContainerStub {
  @NotNull private final TestSourceProvider myInstrumentationTestSourceProvider;

  /**
   * Creates a new {@clink ProductFlavorContainerStub}.
   *
   * @param flavorName    the name of the myFlavor.
   * @param fileStructure the file structure of the Gradle project this {@code ProductFlavorContainer} belongs to.
   */
  TestProductFlavorContainer(@NotNull String flavorName, @NotNull FileStructure fileStructure, @Nullable String dimension) {
    super(new ProductFlavorStub(flavorName, dimension), new TestSourceProvider(fileStructure), Lists.newArrayList());
    myInstrumentationTestSourceProvider = new TestSourceProvider(fileStructure);
    getExtraSourceProviders().add(new SourceProviderContainerStub(ARTIFACT_ANDROID_TEST, myInstrumentationTestSourceProvider));
    setUpPaths(flavorName);
  }

  private void setUpPaths(@NotNull String flavorName) {
    TestSourceProvider mySourceProvider = (TestSourceProvider)getSourceProvider();
    mySourceProvider.addAidlDirectory("src/" + flavorName + "/aidl");
    mySourceProvider.addAssetsDirectory("src/" + flavorName + "/assets");
    mySourceProvider.addJavaDirectory("src/" + flavorName + "/java");
    mySourceProvider.addCDirectory("src/" + flavorName + "/c");
    mySourceProvider.addCppDirectory("src/" + flavorName + "/cpp");
    mySourceProvider.addRenderscriptDirectory("src/" + flavorName + "/renderscript");
    mySourceProvider.addResDirectory("src/" + flavorName + "/rs");
    mySourceProvider.addResourcesDirectory("src/" + flavorName + "/resources");
    mySourceProvider.setManifestFile("src/" + flavorName + "/manifest.xml");

    String nameSuffix = flavorName.equals("main") ? "" : capitalize(flavorName);
    myInstrumentationTestSourceProvider.addAidlDirectory("src/instrumentTest" + nameSuffix + "/aidl");
    myInstrumentationTestSourceProvider.addAssetsDirectory("src/instrumentTest" + nameSuffix + "/assets");
    myInstrumentationTestSourceProvider.addJavaDirectory("src/instrumentTest" + nameSuffix + "/java");
    myInstrumentationTestSourceProvider.addCDirectory("src/instrumentTest" + nameSuffix + "/c");
    myInstrumentationTestSourceProvider.addCppDirectory("src/instrumentTest" + nameSuffix + "/cpp");
    myInstrumentationTestSourceProvider.addRenderscriptDirectory("src/instrumentTest" + nameSuffix + "/renderscript");
    myInstrumentationTestSourceProvider.addResDirectory("src/instrumentTest" + nameSuffix + "/rs");
    myInstrumentationTestSourceProvider.addResourcesDirectory("src/instrumentTest" + nameSuffix + "/resources");
  }
}
