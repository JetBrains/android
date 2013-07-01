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
import com.android.builder.model.*;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

public class AndroidProjectStub implements AndroidProject {
  @NotNull private final Map<String, BuildTypeContainer> myBuildTypes = Maps.newHashMap();
  @NotNull private final Map<String, ProductFlavorContainer> myProductFlavors = Maps.newHashMap();
  @NotNull private final Map<String, Variant> myVariants = Maps.newHashMap();

  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final ProductFlavorContainerStub myDefaultConfig;
  @NotNull private final File myBuildFile;

  @Nullable private VariantStub myFirstVariant;
  private boolean myLibrary;

  public AndroidProjectStub(@NotNull String name) {
    this(name, new FileStructure(name));
  }

  public AndroidProjectStub(@NotNull File parentDir, @NotNull String name) {
    this(name, new FileStructure(parentDir, name));
  }

  private AndroidProjectStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    this.myName = name;
    myFileStructure = fileStructure;
    myDefaultConfig = new ProductFlavorContainerStub("main", myFileStructure);
    myBuildFile = myFileStructure.createProjectFile(SdkConstants.FN_BUILD_GRADLE);
  }

  @Override
  @NotNull
  public String getModelVersion() {
    return "0.5.0-SNAPSHOT";
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  public void setIsLibrary(boolean isLibrary) {
    myLibrary = isLibrary;
  }

  @Override
  public boolean isLibrary() {
    return myLibrary;
  }

  @Override
  @NotNull
  public ProductFlavorContainerStub getDefaultConfig() {
    return myDefaultConfig;
  }

  public BuildTypeContainerStub addBuildType(@NotNull String buildTypeName) {
    BuildTypeContainerStub buildType = new BuildTypeContainerStub(buildTypeName, myFileStructure);
    myBuildTypes.put(buildTypeName, buildType);
    return buildType;
  }

  @Override
  @NotNull
  public Map<String, BuildTypeContainer> getBuildTypes() {
    return myBuildTypes;
  }

  @NotNull
  public ProductFlavorContainerStub addProductFlavor(@NotNull String flavorName) {
    ProductFlavorContainerStub flavor = new ProductFlavorContainerStub(flavorName, myFileStructure);
    myProductFlavors.put(flavorName, flavor);
    return flavor;
  }

  @Override
  @NotNull
  public Map<String, ProductFlavorContainer> getProductFlavors() {
    return myProductFlavors;
  }


  @NotNull
  public VariantStub addVariant(@NotNull String variantName) {
    return addVariant(variantName, variantName);
  }

  @NotNull
  public VariantStub addVariant(@NotNull String variantName, @NotNull String buildTypeName) {
    VariantStub variant = new VariantStub(variantName, buildTypeName, myFileStructure);
    addVariant(variant);
    return variant;
  }

  private void addVariant(@NotNull VariantStub variant) {
    if (myFirstVariant == null) {
      myFirstVariant = variant;
    }
    myVariants.put(variant.getName(), variant);
  }

  @Override
  @NotNull
  public Map<String, Variant> getVariants() {
    return myVariants;
  }

  @Nullable
  public VariantStub getFirstVariant() {
    return myFirstVariant;
  }

  @Override
  @NotNull
  public String getCompileTarget() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public List<String> getBootClasspath() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, SigningConfig> getSigningConfigs() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public AaptOptions getAaptOptions() {
    throw new UnsupportedOperationException();
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
  public File getRootDir() {
    return myFileStructure.getRootDir();
  }

  /**
   * @return this project's build.gradle file.
   */
  @NotNull
  public File getBuildFile() {
    return myBuildFile;
  }
}
