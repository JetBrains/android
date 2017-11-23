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

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.*;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.CMakeModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidModelImpl}.
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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

    assertEquals("buildToolsVersion", "21.0.0", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "android-21", android.compileSdkVersion());
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig());
    assertEquals("flavorDimensions", ImmutableList.of("abi1", "version1"), android.flavorDimensions());
    assertEquals("generatePureSplits", Boolean.FALSE, android.generatePureSplits());
    assertEquals("publishNonDefault", Boolean.TRUE, android.publishNonDefault());
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix());
  }

  public void testAndroidBlockWithDefaultConfigBlock() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
  }

  public void testAndroidBlockWithBuildTypeBlocks() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    type1 {\n" +
                  "      applicationIdSuffix \"typeSuffix-1\"\n" +
                  "    }\n" +
                  "    type2 {\n" +
                  "      applicationIdSuffix \"typeSuffix-2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);
    BuildTypeModel buildType1 = buildTypes.get(0);
    assertEquals("name", "type1", buildType1.name());
    assertEquals("applicationIdSuffix", "typeSuffix-1", buildType1.applicationIdSuffix());
    BuildTypeModel buildType2 = buildTypes.get(1);
    assertEquals("name", "type2", buildType2.name());
    assertEquals("applicationIdSuffix", "typeSuffix-2", buildType2.applicationIdSuffix());
  }

  public void testAndroidBlockWithProductFlavorBlocks() throws Exception {
    String text = "android {\n" +
                  "  productFlavors {\n" +
                  "    flavor1 {\n" +
                  "      applicationId \"com.example.myapplication.flavor1\"\n" +
                  "    }\n" +
                  "    flavor2 {\n" +
                  "      applicationId \"com.example.myapplication.flavor2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);
    ProductFlavorModel flavor1 = productFlavors.get(0);
    assertEquals("name", "flavor1", flavor1.name());
    assertEquals("applicationId", "com.example.myapplication.flavor1", flavor1.applicationId());
    ProductFlavorModel flavor2 = productFlavors.get(1);
    assertEquals("name", "flavor2", flavor2.name());
    assertEquals("applicationId", "com.example.myapplication.flavor2", flavor2.applicationId());
  }

  public void testAndroidBlockWithExternalNativeBuildBlock() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "      path file(\"foo/bar\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", new File("foo/bar"), cmake.path());
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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

    assertNull("flavorDimensions", android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());

    buildModel.resetState();
    assertNull("flavorDimensions", android.flavorDimensions());
  }

  public void testAddToAndResetListElementsWithArgument() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    android.addFlavorDimension("version");
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    buildModel.resetState();
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());
  }

  public void testAddToAndResetListElementsWithMultipleArguments() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

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
    assertNotNull(android);

    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.removeFlavorDimension("version");
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    buildModel.resetState();
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
  }

  public void testAddAndResetDefaultConfigBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);
    assertNull(android.defaultConfig().applicationId());

    android.defaultConfig().setApplicationId("foo.bar");
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());

    buildModel.resetState();
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);
    assertNull(android.defaultConfig().applicationId());
  }

  public void testAddAndResetBuildTypeBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    assertThat(android.buildTypes()).isEmpty();

    android.addBuildType("type");
    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    assertEquals("buildTypes", "type", buildTypes.get(0).name());

    buildModel.resetState();
    assertThat(android.buildTypes()).isEmpty();
  }

  public void testAddAndResetProductFlavorBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    assertThat(android.productFlavors()).isEmpty();

    android.addProductFlavor("flavor");
    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    assertEquals("productFlavors", "flavor", productFlavors.get(0).name());

    buildModel.resetState();
    assertThat(android.productFlavors()).isEmpty();
  }

  public void testRemoveAndResetBuildTypeBlock() throws Exception {
    String text = "android { \n" +
                  "  buildTypes { \n" +
                  "    type1 { \n" +
                  "    } \n" +
                  "    type2 { \n" +
                  "    } \n" +
                  "  } \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);
    assertEquals("buildTypes", "type1", buildTypes.get(0).name());
    assertEquals("buildTypes", "type2", buildTypes.get(1).name());

    android.removeBuildType("type1");
    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    assertEquals("buildTypes", "type2", buildTypes.get(0).name());

    buildModel.resetState();
    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);
    assertEquals("buildTypes", "type1", buildTypes.get(0).name());
    assertEquals("buildTypes", "type2", buildTypes.get(1).name());
  }

  public void testRemoveAndResetProductFlavorBlock() throws Exception {
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
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());
    assertEquals("productFlavors", "flavor2", productFlavors.get(1).name());

    android.removeProductFlavor("flavor2");
    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());

    buildModel.resetState();
    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());
    assertEquals("productFlavors", "flavor2", productFlavors.get(1).name());
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
    assertNotNull(android);

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
    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class);
  }

  public void testAddAndApplyEmptyBuildTypeBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addBuildType("type");
    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    assertEquals("buildTypes", "type", buildTypes.get(0).name());

    applyChanges(buildModel);
    assertThat(android.buildTypes()).isEmpty(); // Empty blocks are not saved to the file.

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android.buildTypes()).isEmpty(); // Empty blocks are not saved to the file.
  }

  public void testAddAndApplyEmptyProductFlavorBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addProductFlavor("flavor");
    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    assertEquals("productFlavors", "flavor", productFlavors.get(0).name());

    applyChanges(buildModel);
    assertThat(android.productFlavors()).isEmpty(); // Empty blocks are not saved to the file.

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android.productFlavors()).isEmpty(); // Empty blocks are not saved to the file.
  }

  public void testAddAndApplyEmptySigningConfigBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addSigningConfig("config");
    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("signingConfigs", "config", signingConfigs.get(0).name());

    applyChanges(buildModel);
    assertThat(android.signingConfigs()).isEmpty(); // Empty blocks are not saved to the file.

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android.signingConfigs()).isEmpty(); // Empty blocks are not saved to the file.
  }

  public void testAddAndApplyEmptySourceSetBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addSourceSet("set");
    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    assertEquals("sourceSets", "set", sourceSets.get(0).name());

    applyChanges(buildModel);
    assertThat(android.sourceSets()).isEmpty(); // Empty blocks are not saved to the file.

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android.sourceSets()).isEmpty(); // Empty blocks are not saved to the file.
  }

  public void testAddAndApplyDefaultConfigBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.defaultConfig().setApplicationId("foo.bar");
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());

    applyChanges(buildModel);
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
  }

  public void testAddAndApplyBuildTypeBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addBuildType("type");
    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    BuildTypeModel buildType = buildTypes.get(0);
    buildType.setApplicationIdSuffix("mySuffix");

    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    buildType = buildTypes.get(0);
    assertEquals("buildTypes", "type", buildType.name());
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix());

    applyChanges(buildModel);
    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    buildType = buildTypes.get(0);
    assertEquals("buildTypes", "type", buildType.name());
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    buildType = buildTypes.get(0);
    assertEquals("buildTypes", "type", buildType.name());
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix());
  }

  public void testAddAndApplyProductFlavorBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addProductFlavor("flavor");
    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    ProductFlavorModel productFlavor = productFlavors.get(0);
    productFlavor.setApplicationId("abc.xyz");

    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    productFlavor = productFlavors.get(0);
    assertEquals("productFlavors", "flavor", productFlavor.name());
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId());

    applyChanges(buildModel);
    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    productFlavor = productFlavors.get(0);
    assertEquals("productFlavors", "flavor", productFlavor.name());
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    productFlavor = productFlavors.get(0);
    assertEquals("productFlavors", "flavor", productFlavor.name());
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId());
  }

  public void testAddAndApplySigningConfigBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addSigningConfig("config");
    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);
    signingConfig.setKeyAlias("myKeyAlias");

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);
    assertEquals("signingConfigs", "config", signingConfig.name());
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias());

    applyChanges(buildModel);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);
    assertEquals("signingConfigs", "config", signingConfig.name());
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);
    assertEquals("signingConfigs", "config", signingConfig.name());
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias());
  }

  public void testAddAndApplySourceSetBlock() throws Exception {
    String text = "android { \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    android.addSourceSet("set");
    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    sourceSet.setRoot("source");

    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    sourceSet = sourceSets.get(0);
    assertEquals("sourceSets", "set", sourceSet.name());
    assertEquals("sourceSets", "source", sourceSet.root());

    applyChanges(buildModel);
    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    sourceSet = sourceSets.get(0);
    assertEquals("sourceSets", "set", sourceSet.name());
    assertEquals("sourceSets", "source", sourceSet.root());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    sourceSet = sourceSets.get(0);
    assertEquals("sourceSets", "set", sourceSet.name());
    assertEquals("sourceSets", "source", sourceSet.root());
  }

  public void testRemoveAndApplyDefaultConfigBlock() throws Exception {
    String text = "android { \n" +
                  "  defaultConfig { \n" +
                  "    applicationId \"foo.bar\"\n" +
                  "  } \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId());
    checkForValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);

    android.defaultConfig().removeApplicationId();
    assertNull(android.defaultConfig().applicationId());
    checkForValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);

    applyChanges(buildModel);
    assertNull(android.defaultConfig().applicationId());
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    assertNull(android.defaultConfig().applicationId());
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);
  }

  public void testRemoveAndApplyBuildTypeBlock() throws Exception {
    String text = "android { \n" +
                  "  buildTypes { \n" +
                  "    type1 { \n" +
                  "    } \n" +
                  "    type2 { \n" +
                  "    } \n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);
    assertEquals("buildTypes", "type1", buildTypes.get(0).name());
    assertEquals("buildTypes", "type2", buildTypes.get(1).name());

    android.removeBuildType("type1");
    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    assertEquals("buildTypes", "type2", buildTypes.get(0).name());

    applyChanges(buildModel);
    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    assertEquals("buildTypes", "type2", buildTypes.get(0).name());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    assertEquals("buildTypes", "type2", buildTypes.get(0).name());
  }

  public void testRemoveAndApplyProductFlavorBlock() throws Exception {
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
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());
    assertEquals("productFlavors", "flavor2", productFlavors.get(1).name());

    android.removeProductFlavor("flavor2");
    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());

    applyChanges(buildModel);
    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);
    assertEquals("productFlavors", "flavor1", productFlavors.get(0).name());
  }

  public void testRemoveAndApplySigningConfigBlock() throws Exception {
    String text = "android { \n" +
                  "  signingConfigs { \n" +
                  "    config1 { \n" +
                  "    } \n" +
                  "    config2 {" +
                  "    } \n" +
                  "  } \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(2);
    assertEquals("signingConfigs", "config1", signingConfigs.get(0).name());
    assertEquals("signingConfigs", "config2", signingConfigs.get(1).name());

    android.removeSigningConfig("config2");
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("signingConfigs", "config1", signingConfigs.get(0).name());

    applyChanges(buildModel);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("signingConfigs", "config1", signingConfigs.get(0).name());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("signingConfigs", "config1", signingConfigs.get(0).name());
  }

  public void testRemoveAndApplySourceSetBlock() throws Exception {
    String text = "android { \n" +
                  "  sourceSets { \n" +
                  "    set1 { \n" +
                  "    } \n" +
                  "    set2 {" +
                  "    } \n" +
                  "  } \n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(2);
    assertEquals("sourceSets", "set1", sourceSets.get(0).name());
    assertEquals("sourceSets", "set2", sourceSets.get(1).name());

    android.removeSourceSet("set2");
    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    assertEquals("sourceSets", "set1", sourceSets.get(0).name());

    applyChanges(buildModel);
    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    assertEquals("sourceSets", "set1", sourceSets.get(0).name());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    assertEquals("sourceSets", "set1", sourceSets.get(0).name());
  }

  public void testRemoveAndApplyBlockApplicationStatements() throws Exception {
    String text = "android.defaultConfig.applicationId \"com.example.myapplication\"\n" +
                  "android.defaultConfig.proguardFiles \"proguard-android.txt\", \"proguard-rules.pro\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());

    defaultConfig.removeApplicationId();
    defaultConfig.removeAllProguardFiles();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertNull(defaultConfig.applicationId());
    assertNull(defaultConfig.proguardFiles());
  }

  public void testAddAndApplyBlockStatements() throws Exception {
    String text = "android.defaultConfig.applicationId \"com.example.myapplication\"\n" +
                  "android.defaultConfig.proguardFiles \"proguard-android.txt\", \"proguard-rules.pro\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());

    defaultConfig.setDimension("abcd");
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion());
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion());
  }

  public void testAddAndApplyLiteralElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);

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
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.replaceFlavorDimension("abi", "xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("xyz", "version"), android.flavorDimensions());
  }

  public void testAddAndApplyListElements() throws Exception {
    String text = "android { \n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    assertNull("flavorDimensions", android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("xyz"), android.flavorDimensions());
  }

  public void testAddToAndApplyListElementsWithOneArgument() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    android.addFlavorDimension("version");
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());
  }

  public void testAddToAndApplyListElementsWithMultipleArguments() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.addFlavorDimension("xyz");
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version", "xyz"), android.flavorDimensions());
  }

  public void testRemoveFromAndApplyListElements() throws Exception {
    String text = "android { \n" +
                  "  flavorDimensions \"abi\", \"version\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi", "version"), android.flavorDimensions());

    android.removeFlavorDimension("version");
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    applyChanges(buildModel);
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertEquals("flavorDimensions", ImmutableList.of("abi"), android.flavorDimensions());
  }
}
