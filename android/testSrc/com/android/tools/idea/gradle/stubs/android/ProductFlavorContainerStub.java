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

import static com.intellij.openapi.util.text.StringUtil.capitalize;

import com.android.SdkConstants;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProviderContainer;
import com.android.tools.idea.gradle.stubs.FileStructure;
import java.util.ArrayList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProductFlavorContainerStub implements ProductFlavorContainer {
  @NotNull private final ProductFlavorStub myFlavor;
  @NotNull private final SourceProviderStub mySourceProvider;
  @NotNull private final SourceProviderStub myInstrumentationTestSourceProvider;

  @NotNull private final Collection<SourceProviderContainer> myExtraArtifactSourceProviders = new ArrayList<>();

  /**
   * Creates a new {@clink ProductFlavorContainerStub}.
   *
   * @param flavorName    the name of the myFlavor.
   * @param fileStructure the file structure of the Gradle project this
   *                      {@code ProductFlavorContainer} belongs to.
   */
  public ProductFlavorContainerStub(
    @NotNull String flavorName,
    @NotNull FileStructure fileStructure,
    @Nullable String dimension
  ) {
    String nameSuffix = flavorName.equals("main") ? "" : capitalize(flavorName);
    myFlavor = new ProductFlavorStub(flavorName, dimension);
    mySourceProvider = new SourceProviderStub(fileStructure, "src/" + flavorName + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myInstrumentationTestSourceProvider =
      new SourceProviderStub(fileStructure, "src/instrumentTest" + nameSuffix + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myExtraArtifactSourceProviders.add(
      new SourceProviderContainerStub(AndroidProject.ARTIFACT_ANDROID_TEST, myInstrumentationTestSourceProvider));
    setUpPaths(flavorName, nameSuffix);
  }

  private void setUpPaths(@NotNull String flavorName, @NotNull String nameSuffix) {
    mySourceProvider.addAidlDirectory("src/" + flavorName + "/aidl");
    mySourceProvider.addAssetsDirectory("src/" + flavorName + "/assets");
    mySourceProvider.addJavaDirectory("src/" + flavorName + "/java");
    mySourceProvider.addCDirectory("src/" + flavorName + "/c");
    mySourceProvider.addCppDirectory("src/" + flavorName + "/cpp");
    mySourceProvider.addRenderscriptDirectory("src/" + flavorName + "/renderscript");
    mySourceProvider.addResDirectory("src/" + flavorName + "/rs");
    mySourceProvider.addResourcesDirectory("src/" + flavorName + "/resources");

    myInstrumentationTestSourceProvider.addAidlDirectory("src/instrumentTest" + nameSuffix + "/aidl");
    myInstrumentationTestSourceProvider.addAssetsDirectory("src/instrumentTest" + nameSuffix + "/assets");
    myInstrumentationTestSourceProvider.addJavaDirectory("src/instrumentTest" + nameSuffix + "/java");
    myInstrumentationTestSourceProvider.addCDirectory("src/instrumentTest" + nameSuffix + "/c");
    myInstrumentationTestSourceProvider.addCppDirectory("src/instrumentTest" + nameSuffix + "/cpp");
    myInstrumentationTestSourceProvider.addRenderscriptDirectory("src/instrumentTest" + nameSuffix + "/renderscript");
    myInstrumentationTestSourceProvider.addResDirectory("src/instrumentTest" + nameSuffix + "/rs");
    myInstrumentationTestSourceProvider.addResourcesDirectory("src/instrumentTest" + nameSuffix + "/resources");
  }

  @Override
  @NotNull
  public ProductFlavorStub getProductFlavor() {
    return myFlavor;
  }

  @Override
  @NotNull
  public SourceProviderStub getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  @NotNull
  public Collection<SourceProviderContainer> getExtraSourceProviders() {
    return myExtraArtifactSourceProviders;
  }

  @NotNull
  public SourceProviderStub getInstrumentationTestSourceProvider() {
    return myInstrumentationTestSourceProvider;
  }
}
