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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * Tests for {@link AndroidModel}.
 */
public class AndroidModelTest extends GradleFileModelTestCase {
  public void testAndroidBlockWithApplicationStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion 23\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());
  }

  public void testAndroidBlockWithAssignmentStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion = \"23.0.0\"\n" +
                  "  compileSdkVersion = \"android-23\"\n" +
                  "  defaultPublishConfig = \"debug\"\n" +
                  "  generatePureSplits = true\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
  }

  public void testAndroidApplicationStatements() throws Exception {
    String text = "android.buildToolsVersion \"23.0.0\"\n" +
                  "android.compileSdkVersion 23\n" +
                  "android.defaultPublishConfig \"debug\"\n" +
                  "android.flavorDimensions \"abi\", \"version\"\n" +
                  "android.generatePureSplits true\n" +
                  "android.publishNonDefault false\n" +
                  "android.resourcePrefix \"abcd\"";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());
  }

  public void testAndroidAssignmentStatements() throws Exception {
    String text = "android.buildToolsVersion = \"23.0.0\"\n" +
                  "android.compileSdkVersion = \"android-23\"\n" +
                  "android.defaultPublishConfig = \"debug\"\n" +
                  "android.generatePureSplits = true";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
  }

  public void testAndroidBlockWithOverrideStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion = \"23.0.0\"\n" +
                  "  compileSdkVersion 23\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "  generatePureSplits = true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}\n" +
                  "android.buildToolsVersion \"21.0.0\"\n" +
                  "android.compileSdkVersion = \"android-21\"\n" +
                  "android.defaultPublishConfig \"release\"\n" +
                  "android.flavorDimensions \"abi1\", \"version1\"\n" +
                  "android.generatePureSplits = false\n" +
                  "android.publishNonDefault true\n" +
                  "android.resourcePrefix \"efgh\"";


    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertEquals("buildToolsVersion", "21.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "android-21", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi1", "version1"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());
  }

  public void testRemoveAndResetElements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion \"23\"\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());

    android.removeBuildToolsVersion();
    android.removeCompileSdkVersion();
    android.removeDefaultPublishConfig();
    android.removeAllFlavorDimensions();
    android.removeGeneratePureSplits();
    android.removePublishNonDefault();
    android.removeResourcePrefix();

    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
    assertNull("defaultPublishConfig", android.defaultPublishConfig());
    assertNull("flavorDimensions", android.flavorDimensions());
    assertNull("generatePureSplits", android.generatePureSplits());
    assertNull("publishNonDefault", android.publishNonDefault());
    assertNull("resourcePrefix", android.resourcePrefix());

    buildModel.resetState();

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());
  }

  public void testEditAndResetLiteralElements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion \"23\"\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());

    android
      .setBuildToolsVersion("24.0.0")
      .setCompileSdkVersion("24")
      .setDefaultPublishConfig("release")
      .setGeneratePureSplits(false)
      .setPublishNonDefault(true)
      .setResourcePrefix("efgh");

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());

    buildModel.resetState();

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());

    // Test the fields that also accept an integer value along with the String valye.
    android
      .setBuildToolsVersion(22)
      .setCompileSdkVersion(21);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());

    buildModel.resetState();

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
  }

  public void testAddAndResetLiteralElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
    assertNull("defaultPublishConfig", android.defaultPublishConfig());
    assertNull("generatePureSplits", android.generatePureSplits());
    assertNull("publishNonDefault", android.publishNonDefault());
    assertNull("resourcePrefix", android.resourcePrefix());

    android
      .setBuildToolsVersion("24.0.0")
      .setCompileSdkVersion("24")
      .setDefaultPublishConfig("release")
      .setGeneratePureSplits(false)
      .setPublishNonDefault(true)
      .setResourcePrefix("efgh");

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());

    buildModel.resetState();

    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
    assertNull("defaultPublishConfig", android.defaultPublishConfig());
    assertNull("generatePureSplits", android.generatePureSplits());
    assertNull("publishNonDefault", android.publishNonDefault());
    assertNull("resourcePrefix", android.resourcePrefix());

    // Test the fields that also accept an integer value along with the String value.
    android
      .setBuildToolsVersion(22)
      .setCompileSdkVersion(21);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());

    buildModel.resetState();

    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
  }

  public void testReplaceAndResetListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.replaceFlavorDimension("abi", "xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());

    buildModel.resetState();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
  }

  public void testAddAndResetListElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNull("flavorDimensions", android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());

    buildModel.resetState();
    assertNull("flavorDimensions", android.flavorDimensions());
  }

  public void testAddToAndResetListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());

    buildModel.resetState();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
  }

  public void testRemoveFromAndResetListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.removeFlavorDimension("version");
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    buildModel.resetState();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
  }

  public void testAddAndResetBlockElements() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertFalse(android.defaultConfig().hasValidPsiElement());
    assertNull(android.defaultConfig().applicationId());
    assertNull(android.productFlavors());

    android.defaultConfig().setApplicationId("foo.bar");
    android.addProductFlavor("flavor");

    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
    Collection<ProductFlavorModel> productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    assertEquals("productFlavors", "flavor", productFlavors.iterator().next().name());

    buildModel.resetState();
    assertFalse(android.defaultConfig().hasValidPsiElement());
    assertNull(android.defaultConfig().applicationId());
    assertNull(android.productFlavors());
  }

  public void testRemoveAndResetBlockElements() throws Exception {
    String text = "android { \n" +
                  "  productFlavors { \n" +
                  "    flavor1 { \n" +
                  "    } \n" +
                  "    flavor2 {" +
                  "    } \n" +
                  "  } \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    Collection<ProductFlavorModel> productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());
    assertSameElements(productFlavors.stream().map(ProductFlavorModel::name).sorted().collect(Collectors.toList()), "flavor1", "flavor2");

    android.removeProductFlavor("flavor1");

    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    Iterator<ProductFlavorModel> iterator = productFlavors.iterator();
    assertEquals("productFlavors", "flavor2", iterator.next().name());

    buildModel.resetState();
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());
    assertSameElements(productFlavors.stream().map(ProductFlavorModel::name).sorted().collect(Collectors.toList()), "flavor1", "flavor2");
  }

  public void testRemoveAndApplyElements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion \"23\"\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());

    android.removeBuildToolsVersion();
    android.removeCompileSdkVersion();
    android.removeDefaultPublishConfig();
    android.removeAllFlavorDimensions();
    android.removeGeneratePureSplits();
    android.removePublishNonDefault();
    android.removeResourcePrefix();

    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
    assertNull("defaultPublishConfig", android.defaultPublishConfig());
    assertNull("flavorDimensions", android.flavorDimensions());
    assertNull("generatePureSplits", android.generatePureSplits());
    assertNull("publishNonDefault", android.publishNonDefault());
    assertNull("resourcePrefix", android.resourcePrefix());

    applyChanges(buildModel);
    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
    assertNull("defaultPublishConfig", android.defaultPublishConfig());
    assertNull("flavorDimensions", android.flavorDimensions());
    assertNull("generatePureSplits", android.generatePureSplits());
    assertNull("publishNonDefault", android.publishNonDefault());
    assertNull("resourcePrefix", android.resourcePrefix());

    buildModel.reparse();
    assertFalse(buildModel.android().hasValidPsiElement());
  }

  public void testAddAndApplyEmptyBlockElements() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    android.addProductFlavor("flavor");

    Collection<ProductFlavorModel> productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    assertEquals("productFlavors", "flavor", productFlavors.iterator().next().name());

    applyChanges(buildModel);
    assertNull(android.productFlavors()); // Empty blocks are not saved to the file.

    buildModel.reparse();
    assertNull(buildModel.android().productFlavors()); // Empty blocks are not saved to the file.
  }

  public void testAddAndApplyBlockElements() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();

    android.defaultConfig().setApplicationId("foo.bar");
    android.addProductFlavor("flavor");
    Collection<ProductFlavorModel> productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    ProductFlavorModel productFlavor = Iterables.getOnlyElement(productFlavors);
    productFlavor.setApplicationId("abc.xyz");

    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    productFlavor = Iterables.getOnlyElement(productFlavors);
    assertEquals("productFlavors", "flavor", productFlavor.name());
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId());

    applyChanges(buildModel);
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    productFlavor = Iterables.getOnlyElement(productFlavors);
    assertEquals("productFlavors", "flavor", productFlavor.name());
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    productFlavor = Iterables.getOnlyElement(productFlavors);
    assertEquals("productFlavors", "flavor", productFlavor.name());
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId());
  }

  public void testRemoveAndApplyBlockElements() throws Exception {
    String text = "android { \n" +
                  "  defaultConfig { \n" +
                  "    applicationId \"foo.bar\"\n" +
                  "  } \n" +
                  "  productFlavors { \n" +
                  "    flavor1 { \n" +
                  "    } \n" +
                  "    flavor2 {" +
                  "    } \n" +
                  "  } \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
    assertTrue(android.defaultConfig().hasValidPsiElement());
    Collection<ProductFlavorModel> productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());
    assertSameElements(productFlavors.stream().map(ProductFlavorModel::name).sorted().collect(Collectors.toList()), "flavor1", "flavor2");

    android.defaultConfig().removeApplicationId();
    android.removeProductFlavor("flavor1");

    assertNull(android.defaultConfig().applicationId());
    assertTrue(android.defaultConfig().hasValidPsiElement());
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    Iterator<ProductFlavorModel> iterator = productFlavors.iterator();
    assertEquals("productFlavors", "flavor2", iterator.next().name());

    applyChanges(buildModel);
    assertNull(android.defaultConfig().applicationId());
    assertFalse(android.defaultConfig().hasValidPsiElement());
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    iterator = productFlavors.iterator();
    assertEquals("productFlavors", "flavor2", iterator.next().name());

    buildModel.reparse();
    android = buildModel.android();
    assertNull(android.defaultConfig().applicationId());
    assertFalse(android.defaultConfig().hasValidPsiElement());
    productFlavors = android.productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 1, productFlavors.size());
    iterator = productFlavors.iterator();
    assertEquals("productFlavors", "flavor2", iterator.next().name());
  }

  public void testRemoveAndApplyBlockApplicationStatements() throws Exception {
    String text = "android.defaultConfig.applicationId \"com.example.myapplication\"\n" +
                  "android.defaultConfig.proguardFiles \"proguard-android.txt\", \"proguard-rules.pro\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());

    defaultConfig.removeApplicationId();
    defaultConfig.removeAllProguardFiles();

    applyChangesAndReparse(buildModel);
    defaultConfig = buildModel.android().defaultConfig();
    assertNull(defaultConfig.applicationId());
    assertNull(defaultConfig.proguardFiles());
  }

  public void testAddAndApplyBlockStatements() throws Exception {
    String text = "android.defaultConfig.applicationId \"com.example.myapplication\"\n" +
                  "android.defaultConfig.proguardFiles \"proguard-android.txt\", \"proguard-rules.pro\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());

    defaultConfig.setDimension("abcd");
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());

    applyChangesAndReparse(buildModel);

    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
  }

  public void testEditAndApplyLiteralElements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion \"23\"\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.TRUE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.FALSE, android.publishNonDefault());
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix());

    android
      .setBuildToolsVersion("24.0.0")
      .setCompileSdkVersion("24")
      .setDefaultPublishConfig("release")
      .setGeneratePureSplits(false)
      .setPublishNonDefault(true)
      .setResourcePrefix("efgh");

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());

    applyChanges(buildModel);

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());
  }

  public void testEditAndApplyIntegerLiteralElements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion \"23\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion());

    android
      .setBuildToolsVersion(22)
      .setCompileSdkVersion(21);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());

    applyChanges(buildModel);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());
  }

  public void testAddAndApplyLiteralElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();

    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());
    assertNull("defaultPublishConfig", android.defaultPublishConfig());
    assertNull("generatePureSplits", android.generatePureSplits());
    assertNull("publishNonDefault", android.publishNonDefault());
    assertNull("resourcePrefix", android.resourcePrefix());

    android
      .setBuildToolsVersion("24.0.0")
      .setCompileSdkVersion("24")
      .setDefaultPublishConfig("release")
      .setGeneratePureSplits(false)
      .setPublishNonDefault(true)
      .setResourcePrefix("efgh");

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());

    applyChanges(buildModel);

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());
  }

  public void testAddAndApplyIntegerLiteralElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();

    assertNull("buildToolsVersion", android.buildToolsVersion());
    assertNull("compileSdkVersion", android.compileSdkVersion());

    android
      .setBuildToolsVersion(22)
      .setCompileSdkVersion(21);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());

    applyChanges(buildModel);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());
  }

  public void testReplaceAndApplyListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.replaceFlavorDimension("abi", "xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());
  }

  public void testAddAndApplyListElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNull("flavorDimensions", android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());
  }

  public void testAddToAndApplyListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());
  }

  public void testRemoveFromAndApplyListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.removeFlavorDimension("version");
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());
  }
}
