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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class AndroidModel {
  private static final String BUILD_TOOLS_VERSION = "buildToolsVersion";
  private static final String COMPILE_SDK_VERSION = "compileSdkVersion";
  private static final String DEFAULT_CONFIG = "defaultConfig";
  private static final String DEFAULT_PUBLISH_CONFIG = "defaultPublishConfig";
  private static final String FLAVOR_DIMENSIONS = "flavorDimensions";
  private static final String GENERATE_PURE_SPLITS = "generatePureSplits";
  private static final String PUBLISH_NON_DEFAULT = "publishNonDefault";
  private static final String RESOURCE_PREFIX = "resourcePrefix";
  // TODO: Add support for useLibrary

  private final AndroidDslElement myDslElement;

  public AndroidModel(@NotNull AndroidDslElement dslElement) {
    myDslElement = dslElement;
  }

  @Nullable
  public String buildToolsVersion() {
    Integer intValue = myDslElement.getProperty(BUILD_TOOLS_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : myDslElement.getProperty(BUILD_TOOLS_VERSION, String.class);
  }

  @NotNull
  public AndroidModel setBuildToolsVersion(int buildToolsVersion) {
    myDslElement.setLiteralProperty(BUILD_TOOLS_VERSION, buildToolsVersion);
    return this;
  }

  @NotNull
  public AndroidModel setBuildToolsVersion(@NotNull String buildToolsVersion) {
    myDslElement.setLiteralProperty(BUILD_TOOLS_VERSION, buildToolsVersion);
    return this;
  }

  @NotNull
  public AndroidModel removeBuildToolsVersion() {
    myDslElement.removeProperty(BUILD_TOOLS_VERSION);
    return this;
  }

  @Nullable
  public String compileSdkVersion() {
    Integer intValue = myDslElement.getProperty(COMPILE_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : myDslElement.getProperty(COMPILE_SDK_VERSION, String.class);
  }

  @NotNull
  public AndroidModel setCompileSdkVersion(int compileSdkVersion) {
    myDslElement.setLiteralProperty(COMPILE_SDK_VERSION, compileSdkVersion);
    return this;
  }

  @NotNull
  public AndroidModel setCompileSdkVersion(@NotNull String compileSdkVersion) {
    myDslElement.setLiteralProperty(COMPILE_SDK_VERSION, compileSdkVersion);
    return this;
  }

  @NotNull
  public AndroidModel removeCompileSdkVersion() {
    myDslElement.removeProperty(COMPILE_SDK_VERSION);
    return this;
  }

  @Nullable
  public ProductFlavorModel defaultConfig() {
    ProductFlavorDslElement parsedDefaultConfig = myDslElement.getProperty(DEFAULT_CONFIG, ProductFlavorDslElement.class);
    return parsedDefaultConfig != null ? new ProductFlavorModel(parsedDefaultConfig) : null;
  }

  @NotNull
  public AndroidModel addDefaultConfig() {
    if (defaultConfig() != null) {
      return this;
    }
    ProductFlavorDslElement defaultConfig = new ProductFlavorDslElement(myDslElement, DEFAULT_CONFIG);
    myDslElement.setNewElement(DEFAULT_CONFIG, defaultConfig);
    return this;
  }

  @NotNull
  public AndroidModel removeDefaultConfig() {
    myDslElement.removeProperty(DEFAULT_CONFIG);
    return this;
  }

  @Nullable
  public String defaultPublishConfig() {
    return myDslElement.getProperty(DEFAULT_PUBLISH_CONFIG, String.class);
  }

  @NotNull
  public AndroidModel setDefaultPublishConfig(@NotNull String defaultPublishConfig) {
    myDslElement.setLiteralProperty(DEFAULT_PUBLISH_CONFIG, defaultPublishConfig);
    return this;
  }

  @NotNull
  public AndroidModel removeDefaultPublishConfig() {
    myDslElement.removeProperty(DEFAULT_PUBLISH_CONFIG);
    return this;
  }

  @Nullable
  public List<String> flavorDimensions() {
    return myDslElement.getListProperty(FLAVOR_DIMENSIONS, String.class);
  }

  @NotNull
  public AndroidModel addFlavorDimension(@NotNull String flavorDimension) {
    myDslElement.addToListProperty(FLAVOR_DIMENSIONS, flavorDimension);
    return this;
  }

  @NotNull
  public AndroidModel removeFlavorDimension(@NotNull String flavorDimension) {
    myDslElement.removeFromListProperty(FLAVOR_DIMENSIONS, flavorDimension);
    return this;
  }

  @NotNull
  public AndroidModel removeAllFlavorDimensions() {
    myDslElement.removeProperty(FLAVOR_DIMENSIONS);
    return this;
  }

  @NotNull
  public AndroidModel replaceFlavorDimension(@NotNull String oldFlavorDimension, @NotNull String newFlavorDimension) {
    myDslElement.replaceInListProperty(FLAVOR_DIMENSIONS, oldFlavorDimension, newFlavorDimension);
    return this;
  }

  @Nullable
  public Boolean generatePureSplits() {
    return myDslElement.getProperty(GENERATE_PURE_SPLITS, Boolean.class);
  }

  @NotNull
  public AndroidModel setGeneratePureSplits(boolean generatePureSplits) {
    myDslElement.setLiteralProperty(GENERATE_PURE_SPLITS, generatePureSplits);
    return this;
  }

  @NotNull
  public AndroidModel removeGeneratePureSplits() {
    myDslElement.removeProperty(GENERATE_PURE_SPLITS);
    return this;
  }

  @Nullable
  public Collection<ProductFlavorModel> productFlavors() {
    ProductFlavorsDslElement productFlavors =
      myDslElement.getProperty(ProductFlavorsDslElement.NAME, ProductFlavorsDslElement.class);
    return productFlavors == null ? null : productFlavors.get();
  }

  @NotNull
  public AndroidModel addProductFlavor(@NotNull String flavor) {
    ProductFlavorsDslElement productFlavors =
      myDslElement.getProperty(ProductFlavorsDslElement.NAME, ProductFlavorsDslElement.class);
    if (productFlavors == null) {
      productFlavors = new ProductFlavorsDslElement(myDslElement);
      myDslElement.setNewElement(ProductFlavorsDslElement.NAME, productFlavors);
    }

    ProductFlavorDslElement flavorElement = productFlavors.getProperty(flavor, ProductFlavorDslElement.class);
    if (flavorElement == null) {
      flavorElement = new ProductFlavorDslElement(productFlavors, flavor);
      productFlavors.setNewElement(flavor, flavorElement);
    }
    return this;
  }

  @NotNull
  public AndroidModel removeProductFlavor(@NotNull String flavor) {
    ProductFlavorsDslElement productFlavors =
      myDslElement.getProperty(ProductFlavorsDslElement.NAME, ProductFlavorsDslElement.class);
    if (productFlavors != null) {
      productFlavors.removeProperty(flavor);
    }
    return this;
  }

  @Nullable
  public Boolean publishNonDefault() {
    return myDslElement.getProperty(PUBLISH_NON_DEFAULT, Boolean.class);
  }

  @NotNull
  public AndroidModel setPublishNonDefault(boolean publishNonDefault) {
    myDslElement.setLiteralProperty(PUBLISH_NON_DEFAULT, publishNonDefault);
    return this;
  }

  @NotNull
  public AndroidModel removePublishNonDefault() {
    myDslElement.removeProperty(PUBLISH_NON_DEFAULT);
    return this;
  }

  @Nullable
  public String resourcePrefix() {
    return myDslElement.getProperty(RESOURCE_PREFIX, String.class);
  }

  @NotNull
  public AndroidModel setResourcePrefix(@NotNull String resourcePrefix) {
    myDslElement.setLiteralProperty(RESOURCE_PREFIX, resourcePrefix);
    return this;
  }

  @NotNull
  public AndroidModel removeResourcePrefix() {
    myDslElement.removeProperty(RESOURCE_PREFIX);
    return this;
  }
}
