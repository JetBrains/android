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
package com.android.tools.idea.gradle.dsl.android;

import com.android.tools.idea.gradle.dsl.GradleDslElement;
import com.android.tools.idea.gradle.dsl.GradleDslPropertiesElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class AndroidElement extends GradleDslPropertiesElement {
  public static final String NAME = "android";

  private static final String BUILD_TOOLS_VERSION = "buildToolsVersion";
  private static final String COMPILE_SDK_VERSION = "compileSdkVersion";
  private static final String DEFAULT_CONFIG = "defaultConfig";
  private static final String DEFAULT_PUBLISH_CONFIG = "defaultPublishConfig";
  private static final String FLAVOR_DIMENSIONS = "flavorDimensions";
  private static final String GENERATE_PURE_SPLITS = "generatePureSplits";
  private static final String PUBLISH_NON_DEFAULT = "publishNonDefault";
  private static final String RESOURCE_PREFIX = "resourcePrefix";
  // TODO: Add support for useLibrary

  public AndroidElement(@Nullable GradleDslElement parent) {
    super(parent);
  }

  @Nullable
  public String buildToolsVersion() {
    Integer intValue = getProperty(BUILD_TOOLS_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(BUILD_TOOLS_VERSION, String.class);
  }

  @NotNull
  public AndroidElement setBuildToolsVersion(int buildToolsVersion) {
    return (AndroidElement)setLiteralProperty(BUILD_TOOLS_VERSION, buildToolsVersion);
  }

  @NotNull
  public AndroidElement setBuildToolsVersion(@NotNull String buildToolsVersion) {
    return (AndroidElement)setLiteralProperty(BUILD_TOOLS_VERSION, buildToolsVersion);
  }

  @Nullable
  public String compileSdkVersion() {
    Integer intValue = getProperty(COMPILE_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(COMPILE_SDK_VERSION, String.class);
  }

  @NotNull
  public AndroidElement setCompileSdkVersion(int compileSdkVersion) {
    return (AndroidElement)setLiteralProperty(COMPILE_SDK_VERSION, compileSdkVersion);
  }

  @NotNull
  public AndroidElement setCompileSdkVersion(@NotNull String compileSdkVersion) {
    return (AndroidElement)setLiteralProperty(COMPILE_SDK_VERSION, compileSdkVersion);
  }

  @Nullable
  public ProductFlavorElement defaultConfig() {
    return getProperty(DEFAULT_CONFIG, ProductFlavorElement.class);
  }

  @NotNull
  public AndroidElement createDefaultConfig() {
    assert defaultConfig() == null;
    ProductFlavorElement defaultConfig = new ProductFlavorElement(this, DEFAULT_CONFIG);
    return (AndroidElement)setNewElement(DEFAULT_CONFIG, defaultConfig);
  }

  @Nullable
  public String defaultPublishConfig() {
    return getProperty(DEFAULT_PUBLISH_CONFIG, String.class);
  }

  @NotNull
  public AndroidElement setDefaultPublishConfig(@NotNull String defaultPublishConfig) {
    return (AndroidElement)setLiteralProperty(DEFAULT_PUBLISH_CONFIG, defaultPublishConfig);
  }

  @Nullable
  public List<String> flavorDimensions() {
    return getListProperty(FLAVOR_DIMENSIONS, String.class);
  }

  @NotNull
  public AndroidElement addFlavorDimension(@NotNull String flavorDimension) {
    return (AndroidElement)addToListProperty(FLAVOR_DIMENSIONS, flavorDimension);
  }

  @NotNull
  public AndroidElement removeFlavorDimension(@NotNull String flavorDimension) {
    return (AndroidElement)removeFromListProperty(FLAVOR_DIMENSIONS, flavorDimension);
  }

  @NotNull
  public AndroidElement replaceFlavorDimension(@NotNull String oldFlavorDimension, @NotNull String newFlavorDimension) {
    return (AndroidElement)replaceInListProperty(FLAVOR_DIMENSIONS, oldFlavorDimension, newFlavorDimension);
  }

  @Nullable
  public Boolean generatePureSplits() {
    return getProperty(GENERATE_PURE_SPLITS, Boolean.class);
  }

  @NotNull
  public AndroidElement setGeneratePureSplits(boolean generatePureSplits) {
    return (AndroidElement)setLiteralProperty(GENERATE_PURE_SPLITS, generatePureSplits);
  }

  @Nullable
  public Collection<ProductFlavorElement> productFlavors() {
    ProductFlavorsElement productFlavors = getProperty(ProductFlavorsElement.NAME, ProductFlavorsElement.class);
    return productFlavors == null ? null : productFlavors.get();
  }

  @NotNull
  public AndroidElement addProductFlavor(@NotNull String flavor) {
    ProductFlavorsElement productFlavors = getProperty(ProductFlavorsElement.NAME, ProductFlavorsElement.class);
    if (productFlavors == null) {
      productFlavors = new ProductFlavorsElement(this);
      setNewElement(ProductFlavorsElement.NAME, productFlavors);
    }

    ProductFlavorElement flavorElement = productFlavors.getProperty(flavor, ProductFlavorElement.class);
    if (flavorElement == null) {
      flavorElement = new ProductFlavorElement(this, flavor);
      productFlavors.setNewElement(flavor, flavorElement);
    }
    return this;
  }

  @NotNull
  public AndroidElement removeProductFlavor(@NotNull String flavor) {
    ProductFlavorsElement productFlavors = getProperty(ProductFlavorsElement.NAME, ProductFlavorsElement.class);
    if (productFlavors != null) {
      productFlavors.removeProperty(flavor);
    }
    return this;
  }

  @Nullable
  public Boolean publishNonDefault() {
    return getProperty(PUBLISH_NON_DEFAULT, Boolean.class);
  }

  @NotNull
  public AndroidElement setPublishNonDefault(boolean publishNonDefault) {
    return (AndroidElement)setLiteralProperty(PUBLISH_NON_DEFAULT, publishNonDefault);
  }

  @Nullable
  public String resourcePrefix() {
    return getProperty(RESOURCE_PREFIX, String.class);
  }

  @NotNull
  public AndroidElement setResourcePrefix(@NotNull String resourcePrefix) {
    return (AndroidElement)setLiteralProperty(RESOURCE_PREFIX, resourcePrefix);
  }
}
