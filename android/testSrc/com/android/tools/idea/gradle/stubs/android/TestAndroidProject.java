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

import com.android.ide.common.gradle.model.stubs.AndroidProjectStub;
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub;
import com.android.ide.common.gradle.model.stubs.LintOptionsStub;
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

public class TestAndroidProject extends AndroidProjectStub {
  private static final Collection<String> NO_UNRESOLVED_DEPENDENCIES = ImmutableList.of();

  @NotNull private final FileStructure myFileStructure;

  @Nullable private TestVariant myFirstVariant;

  public TestAndroidProject(@NotNull String name) {
    this(name, new FileStructure(name));
  }

  public TestAndroidProject(@NotNull File parentFolderPath, @NotNull String name) {
    this(name, new FileStructure(parentFolderPath, name));
  }

  private TestAndroidProject(@NotNull String name, @NotNull FileStructure fileStructure) {
    super(DEFAULT_MODEL_VERSION, name, new ProductFlavorContainerStub(),
          Lists.newArrayList() /* build types */, Lists.newArrayList() /* product flavors */, "25",
          Lists.newArrayList() /* sync issues */, Lists.newArrayList() /* variants */,
          Lists.newArrayList() /* flavor dimensions */, "compileTarget", Lists.newArrayList() /* boot classpath */,
          Lists.newArrayList() /* native toolchains */, Lists.newArrayList() /* signing configs */,
          new LintOptionsStub(), NO_UNRESOLVED_DEPENDENCIES, new JavaCompileOptionsStub(),
          fileStructure.createProjectFolder("build"), null, 3, PROJECT_TYPE_APP, GENERATION_ORIGINAL, false);
    myFileStructure = fileStructure;
  }

  @Override
  public int getApiVersion() {
    return 3;
  }

  @NotNull
  public TestBuildTypeContainer addBuildType(@NotNull String buildTypeName) {
    TestBuildTypeContainer buildType = new TestBuildTypeContainer(buildTypeName, myFileStructure);
    addBuildType(buildType);
    return buildType;
  }

  @NotNull
  public TestProductFlavorContainer addProductFlavor(@NotNull String flavorName, @Nullable String flavorDimension) {
    addFlavorDimension(flavorDimension);
    TestProductFlavorContainer productFlavor = new TestProductFlavorContainer(flavorName, myFileStructure, flavorDimension);
    addProductFlavor(productFlavor);
    return productFlavor;
  }

  @NotNull
  public TestVariant addVariant(@NotNull String variantName) {
    return addVariant(variantName, variantName);
  }

  @NotNull
  public TestVariant addVariant(@NotNull String variantName, @NotNull String buildTypeName) {
    TestVariant variant = new TestVariant(variantName, buildTypeName, myFileStructure);
    addVariant(variant);
    return variant;
  }

  private void addVariant(@NotNull TestVariant variant) {
    if (myFirstVariant == null) {
      myFirstVariant = variant;
    }
    super.addVariant(variant);
  }

  @Nullable
  public TestVariant getFirstVariant() {
    return myFirstVariant;
  }

  /**
   * Deletes this project's directory structure.
   */
  public void dispose() {
    myFileStructure.dispose();
  }

  /**
   * @return this project's root directory.
   */
  @NotNull
  public File getRootFolderPath() {
    return myFileStructure.getRootFolderPath();
  }
}
