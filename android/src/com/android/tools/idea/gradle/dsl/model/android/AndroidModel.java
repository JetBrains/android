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

import com.android.tools.idea.gradle.dsl.parser.android.AndroidPsiElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorPsiElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsPsiElement;
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

  private final AndroidPsiElement myPsiElement;

  public AndroidModel(@NotNull AndroidPsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Nullable
  public String buildToolsVersion() {
    Integer intValue = myPsiElement.getProperty(BUILD_TOOLS_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : myPsiElement.getProperty(BUILD_TOOLS_VERSION, String.class);
  }

  @NotNull
  public AndroidModel setBuildToolsVersion(int buildToolsVersion) {
    myPsiElement.setLiteralProperty(BUILD_TOOLS_VERSION, buildToolsVersion);
    return this;
  }

  @NotNull
  public AndroidModel setBuildToolsVersion(@NotNull String buildToolsVersion) {
    myPsiElement.setLiteralProperty(BUILD_TOOLS_VERSION, buildToolsVersion);
    return this;
  }

  @NotNull
  public AndroidModel removeBuildToolsVersion() {
    myPsiElement.removeProperty(BUILD_TOOLS_VERSION);
    return this;
  }

  @Nullable
  public String compileSdkVersion() {
    Integer intValue = myPsiElement.getProperty(COMPILE_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : myPsiElement.getProperty(COMPILE_SDK_VERSION, String.class);
  }

  @NotNull
  public AndroidModel setCompileSdkVersion(int compileSdkVersion) {
    myPsiElement.setLiteralProperty(COMPILE_SDK_VERSION, compileSdkVersion);
    return this;
  }

  @NotNull
  public AndroidModel setCompileSdkVersion(@NotNull String compileSdkVersion) {
    myPsiElement.setLiteralProperty(COMPILE_SDK_VERSION, compileSdkVersion);
    return this;
  }

  @NotNull
  public AndroidModel removeCompileSdkVersion() {
    myPsiElement.removeProperty(COMPILE_SDK_VERSION);
    return this;
  }

  @Nullable
  public ProductFlavorModel defaultConfig() {
    ProductFlavorPsiElement parsedDefaultConfig = myPsiElement.getProperty(DEFAULT_CONFIG, ProductFlavorPsiElement.class);
    return parsedDefaultConfig != null ? new ProductFlavorModel(parsedDefaultConfig) : null;
  }

  @NotNull
  public AndroidModel addDefaultConfig() {
    if (defaultConfig() != null) {
      return this;
    }
    ProductFlavorPsiElement defaultConfig = new ProductFlavorPsiElement(myPsiElement, DEFAULT_CONFIG);
    myPsiElement.setNewElement(DEFAULT_CONFIG, defaultConfig);
    return this;
  }

  @NotNull
  public AndroidModel removeDefaultConfig() {
    myPsiElement.removeProperty(DEFAULT_CONFIG);
    return this;
  }

  @Nullable
  public String defaultPublishConfig() {
    return myPsiElement.getProperty(DEFAULT_PUBLISH_CONFIG, String.class);
  }

  @NotNull
  public AndroidModel setDefaultPublishConfig(@NotNull String defaultPublishConfig) {
    myPsiElement.setLiteralProperty(DEFAULT_PUBLISH_CONFIG, defaultPublishConfig);
    return this;
  }

  @NotNull
  public AndroidModel removeDefaultPublishConfig() {
    myPsiElement.removeProperty(DEFAULT_PUBLISH_CONFIG);
    return this;
  }

  @Nullable
  public List<String> flavorDimensions() {
    return myPsiElement.getListProperty(FLAVOR_DIMENSIONS, String.class);
  }

  @NotNull
  public AndroidModel addFlavorDimension(@NotNull String flavorDimension) {
    myPsiElement.addToListProperty(FLAVOR_DIMENSIONS, flavorDimension);
    return this;
  }

  @NotNull
  public AndroidModel removeFlavorDimension(@NotNull String flavorDimension) {
    myPsiElement.removeFromListProperty(FLAVOR_DIMENSIONS, flavorDimension);
    return this;
  }

  @NotNull
  public AndroidModel removeAllFlavorDimensions() {
    myPsiElement.removeProperty(FLAVOR_DIMENSIONS);
    return this;
  }

  @NotNull
  public AndroidModel replaceFlavorDimension(@NotNull String oldFlavorDimension, @NotNull String newFlavorDimension) {
    myPsiElement.replaceInListProperty(FLAVOR_DIMENSIONS, oldFlavorDimension, newFlavorDimension);
    return this;
  }

  @Nullable
  public Boolean generatePureSplits() {
    return myPsiElement.getProperty(GENERATE_PURE_SPLITS, Boolean.class);
  }

  @NotNull
  public AndroidModel setGeneratePureSplits(boolean generatePureSplits) {
    myPsiElement.setLiteralProperty(GENERATE_PURE_SPLITS, generatePureSplits);
    return this;
  }

  @NotNull
  public AndroidModel removeGeneratePureSplits() {
    myPsiElement.removeProperty(GENERATE_PURE_SPLITS);
    return this;
  }

  @Nullable
  public Collection<ProductFlavorModel> productFlavors() {
    ProductFlavorsPsiElement productFlavors =
      myPsiElement.getProperty(ProductFlavorsPsiElement.NAME, ProductFlavorsPsiElement.class);
    return productFlavors == null ? null : productFlavors.get();
  }

  @NotNull
  public AndroidModel addProductFlavor(@NotNull String flavor) {
    ProductFlavorsPsiElement productFlavors =
      myPsiElement.getProperty(ProductFlavorsPsiElement.NAME, ProductFlavorsPsiElement.class);
    if (productFlavors == null) {
      productFlavors = new ProductFlavorsPsiElement(myPsiElement);
      myPsiElement.setNewElement(ProductFlavorsPsiElement.NAME, productFlavors);
    }

    ProductFlavorPsiElement flavorElement = productFlavors.getProperty(flavor, ProductFlavorPsiElement.class);
    if (flavorElement == null) {
      flavorElement = new ProductFlavorPsiElement(productFlavors, flavor);
      productFlavors.setNewElement(flavor, flavorElement);
    }
    return this;
  }

  @NotNull
  public AndroidModel removeProductFlavor(@NotNull String flavor) {
    ProductFlavorsPsiElement productFlavors =
      myPsiElement.getProperty(ProductFlavorsPsiElement.NAME, ProductFlavorsPsiElement.class);
    if (productFlavors != null) {
      productFlavors.removeProperty(flavor);
    }
    return this;
  }

  @Nullable
  public Boolean publishNonDefault() {
    return myPsiElement.getProperty(PUBLISH_NON_DEFAULT, Boolean.class);
  }

  @NotNull
  public AndroidModel setPublishNonDefault(boolean publishNonDefault) {
    myPsiElement.setLiteralProperty(PUBLISH_NON_DEFAULT, publishNonDefault);
    return this;
  }

  @NotNull
  public AndroidModel removePublishNonDefault() {
    myPsiElement.removeProperty(PUBLISH_NON_DEFAULT);
    return this;
  }

  @Nullable
  public String resourcePrefix() {
    return myPsiElement.getProperty(RESOURCE_PREFIX, String.class);
  }

  @NotNull
  public AndroidModel setResourcePrefix(@NotNull String resourcePrefix) {
    myPsiElement.setLiteralProperty(RESOURCE_PREFIX, resourcePrefix);
    return this;
  }

  @NotNull
  public AndroidModel removeResourcePrefix() {
    myPsiElement.removeProperty(RESOURCE_PREFIX);
    return this;
  }
}
