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

import com.android.builder.model.ProductFlavorContainer;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class ProductFlavorContainerStub implements ProductFlavorContainer {
  @NotNull private final ProductFlavorStub myFlavor;
  @NotNull private final SourceProviderStub mySourceProvider;
  @NotNull private final SourceProviderStub myTestSourceProvider;

  /**
   * Creates a new {@clink ProductFlavorContainerStub}.
   *
   * @param flavorName    the name of the myFlavor.
   * @param fileStructure the file structure of the Gradle project this
   *                      {@code ProductFlavorContainer} belongs to.
   */
  ProductFlavorContainerStub(@NotNull String flavorName, @NotNull FileStructure fileStructure) {
    myFlavor = new ProductFlavorStub(flavorName);
    mySourceProvider = new SourceProviderStub(fileStructure);
    myTestSourceProvider = new SourceProviderStub(fileStructure);
    setUpPaths(flavorName);
  }

  private void setUpPaths(@NotNull String flavorName) {
    mySourceProvider.addAidlDirectory("src/" + flavorName + "/aidl");
    mySourceProvider.addAssetsDirectory("src/" + flavorName + "/assets");
    mySourceProvider.addJavaDirectory("src/" + flavorName + "/java");
    mySourceProvider.addJniDirectory("src/" + flavorName + "/jni");
    mySourceProvider.addRenderscriptDirectory("src/" + flavorName + "/renderscript");
    mySourceProvider.addResDirectory("src/" + flavorName + "/rs");
    mySourceProvider.addResourcesDirectory("src/" + flavorName + "/resources");
    mySourceProvider.setManifestFile("src/" + flavorName + "/manifest.xml");

    String nameSuffix = flavorName.equals("main") ? "" : StringUtil.capitalize(flavorName);

    myTestSourceProvider.addAidlDirectory("src/instrumentTest" + nameSuffix + "/aidl");
    myTestSourceProvider.addAssetsDirectory("src/instrumentTest" + nameSuffix + "/assets");
    myTestSourceProvider.addJavaDirectory("src/instrumentTest" + nameSuffix + "/java");
    myTestSourceProvider.addJniDirectory("src/instrumentTest" + nameSuffix + "/jni");
    myTestSourceProvider.addRenderscriptDirectory("src/instrumentTest" + nameSuffix + "/renderscript");
    myTestSourceProvider.addResDirectory("src/instrumentTest" + nameSuffix + "/rs");
    myTestSourceProvider.addResourcesDirectory("src/instrumentTest" + nameSuffix + "/resources");
  }

  @NotNull
  @Override
  public ProductFlavorStub getProductFlavor() {
    return myFlavor;
  }

  @NotNull
  @Override
  public SourceProviderStub getSourceProvider() {
    return mySourceProvider;
  }

  @NotNull
  @Override
  public SourceProviderStub getTestSourceProvider() {
    return myTestSourceProvider;
  }
}
