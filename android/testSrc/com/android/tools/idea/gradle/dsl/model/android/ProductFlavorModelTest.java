/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License atA
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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.CMakeOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.NdkBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.ResValueImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.CMakeOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.NdkBuildOptionsModelImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link ProductFlavorModelImpl}.
 *
 * <p>Both {@code android.defaultConfig {}} and {@code android.productFlavors.xyz {}} uses the same structure with same attributes.
 * In this test, the product flavor structure defined by {@link ProductFlavorModelImpl} is tested in great deal to cover all combinations using
 * the {@code android.defaultConfig {}} block. The general structure of {@code android.productFlavors {}} is tested in
 * {@link ProductFlavorModelTest}.
 */
public class ProductFlavorModelTest extends GradleFileModelTestCase {
  public void testDefaultConfigBlockWithApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    dimension \"abcd\"\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    maxSdkVersion 23\n" +
                  "    minSdkVersion 15\n" +
                  "    multiDexEnabled true\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "    targetSdkVersion 22 \n" +
                  "    testApplicationId \"com.example.myapplication.test\"\n" +
                  "    testFunctionalTest true\n" +
                  "    testHandleProfiling true\n" +
                  "    testInstrumentationRunner \"abcd\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "    useJack true\n" +
                  "    versionCode 1\n" +
                  "    versionName \"1.0\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());
  }

  public void testDefaultConfigBlockWithAssignmentStatements() throws Exception {
    String text = "android.defaultConfig {\n" +
                  "  applicationId = \"com.example.myapplication\"\n" +
                  "  consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "  dimension = \"abcd\"\n" +
                  "  manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "  maxSdkVersion = 23\n" +
                  "  multiDexEnabled = true\n" +
                  "  proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "  testApplicationId = \"com.example.myapplication.test\"\n" +
                  "  testFunctionalTest = true\n" +
                  "  testHandleProfiling = true\n" +
                  "  testInstrumentationRunner = \"abcd\"\n" +
                  "  testInstrumentationRunnerArguments = [size:\"medium\", foo:\"bar\"]\n" +
                  "  useJack = true\n" +
                  "  versionCode = 1\n" +
                  "  versionName = \"1.0\"\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());
  }

  public void testDefaultConfigApplicationStatements() throws Exception {
    String text = "android.defaultConfig.applicationId \"com.example.myapplication\"\n" +
                  "android.defaultConfig.consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "android.defaultConfig.dimension \"abcd\"\n" +
                  "android.defaultConfig.manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "android.defaultConfig.maxSdkVersion 23\n" +
                  "android.defaultConfig.minSdkVersion 15\n" +
                  "android.defaultConfig.multiDexEnabled true\n" +
                  "android.defaultConfig.proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "android.defaultConfig.resConfigs \"abcd\", \"efgh\"\n" +
                  "android.defaultConfig.resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "android.defaultConfig.targetSdkVersion 22 \n" +
                  "android.defaultConfig.testApplicationId \"com.example.myapplication.test\"\n" +
                  "android.defaultConfig.testFunctionalTest true\n" +
                  "android.defaultConfig.testHandleProfiling true\n" +
                  "android.defaultConfig.testInstrumentationRunner \"abcd\"\n" +
                  "android.defaultConfig.testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "android.defaultConfig.useJack true\n" +
                  "android.defaultConfig.versionCode 1\n" +
                  "android.defaultConfig.versionName \"1.0\"";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());
  }

  public void testDefaultConfigAssignmentStatements() throws Exception {
    String text = "android.defaultConfig.applicationId = \"com.example.myapplication\"\n" +
                  "android.defaultConfig.consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "android.defaultConfig.dimension = \"abcd\"\n" +
                  "android.defaultConfig.manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "android.defaultConfig.maxSdkVersion = 23\n" +
                  "android.defaultConfig.multiDexEnabled = true\n" +
                  "android.defaultConfig.proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "android.defaultConfig.testApplicationId = \"com.example.myapplication.test\"\n" +
                  "android.defaultConfig.testFunctionalTest = true\n" +
                  "android.defaultConfig.testHandleProfiling = true\n" +
                  "android.defaultConfig.testInstrumentationRunner = \"abcd\"\n" +
                  "android.defaultConfig.testInstrumentationRunnerArguments = [size:\"medium\", foo:\"bar\"]\n" +
                  "android.defaultConfig.useJack = true\n" +
                  "android.defaultConfig.versionCode = 1\n" +
                  "android.defaultConfig.versionName = \"1.0\"";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());
  }

  public void testDefaultConfigBlockWithOverrideStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "    consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    dimension \"abcd\"\n" +
                  "    manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "    maxSdkVersion 23\n" +
                  "    minSdkVersion 15\n" +
                  "    multiDexEnabled true\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    targetSdkVersion 22 \n" +
                  "    testApplicationId \"com.example.myapplication.test\"\n" +
                  "    testFunctionalTest true\n" +
                  "    testHandleProfiling = false\n" +
                  "    testInstrumentationRunner = \"abcd\"\n" +
                  "    testInstrumentationRunnerArguments = [size:\"medium\", foo:\"bar\"]\n" +
                  "    useJack true\n" +
                  "    versionCode 1\n" +
                  "    versionName \"1.0\"\n" +
                  "  }\n" +
                  "}\n" +
                  "android.defaultConfig {\n" +
                  "  applicationId = \"com.example.myapplication1\"\n" +
                  "  consumerProguardFiles 'proguard-android-1.txt', 'proguard-rules-1.pro'\n" +
                  "  dimension \"efgh\"\n" +
                  "  manifestPlaceholders activityLabel3:\"defaultName3\", activityLabel4:\"defaultName4\"\n" +
                  "  maxSdkVersion = 24\n" +
                  "  minSdkVersion 16\n" +
                  "  multiDexEnabled false\n" +
                  "  proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.pro']\n" +
                  "  targetSdkVersion 23 \n" +
                  "  testApplicationId = \"com.example.myapplication.test1\"\n" +
                  "  testFunctionalTest false\n" +
                  "  testHandleProfiling true\n" +
                  "  testInstrumentationRunner = \"efgh\"\n" +
                  "  testInstrumentationRunnerArguments = [key:\"value\"]\n" +
                  "  useJack = false\n" +
                  "  versionCode 2\n" +
                  "  versionName = \"2.0\"\n" +
                  "}\n" +
                  "android.defaultConfig.versionName = \"3.0\"";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication1", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel3", "defaultName3", "activityLabel4", "defaultName4"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"), defaultConfig.proguardFiles());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test1", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key", "value"), defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 2, defaultConfig.versionCode());
    assertEquals("versionName", "3.0", defaultConfig.versionName());
  }

  public void testDefaultConfigBlockWithAppendStatements() throws Exception {
    String text = "android.defaultConfig {\n" +
                  "  proguardFiles = ['pro-1.txt', 'pro-2.txt']\n" +
                  "  resConfigs \"abcd\", \"efgh\"\n" +
                  "  resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "  testInstrumentationRunnerArguments = [key1:\"value1\", key2:\"value2\"]\n" +
                  "  testInstrumentationRunnerArgument \"key3\", \"value3\"\n" +
                  "}\n" +
                  "android { \n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    proguardFile 'pro-3.txt'\n" +
                  "    resConfigs \"ijkl\", \"mnop\"\n" +
                  "    resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    testInstrumentationRunnerArguments key4:\"value4\", key5:\"value5\"\n" +
                  "  }\n" +
                  "}\n" +
                  "android.defaultConfig.manifestPlaceholders.activityLabel3 \"defaultName3\"\n" +
                  "android.defaultConfig.manifestPlaceholders.activityLabel4 = \"defaultName4\"\n" +
                  "android.defaultConfig.proguardFiles 'pro-4.txt', 'pro-5.txt'\n" +
                  "android.defaultConfig.resConfig \"qrst\"\n" +
                  "android.defaultConfig.testInstrumentationRunnerArguments.key6 \"value6\"\n" +
                  "android.defaultConfig.testInstrumentationRunnerArguments.key7 = \"value7\"";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap
      .of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2", "activityLabel3", "defaultName3", "activityLabel4",
          "defaultName4"), defaultConfig.manifestPlaceholders());
    assertEquals("proguardFiles", ImmutableList.of("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "ijkl", "mnop", "qrst"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());
    Map<String, String> expected =
      ImmutableMap.<String, String>builder().put("key1", "value1").put("key2", "value2").put("key3", "value3").put("key4", "value4")
        .put("key5", "value5").put("key6", "value6").put("key7", "value7").build();
    assertEquals("testInstrumentationRunnerArguments", expected, defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testDefaultConfigMapStatements() throws Exception {
    String text = "android.defaultConfig.manifestPlaceholders.activityLabel1 \"defaultName1\"\n" +
                  "android.defaultConfig.manifestPlaceholders.activityLabel2 = \"defaultName2\"\n" +
                  "android.defaultConfig.testInstrumentationRunnerArguments.key1 \"value1\"\n" +
                  "android.defaultConfig.testInstrumentationRunnerArguments.key2 = \"value2\"";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testRemoveAndResetElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    dimension \"abcd\"\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    maxSdkVersion 23\n" +
                  "    minSdkVersion 15\n" +
                  "    multiDexEnabled true\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "    targetSdkVersion 22 \n" +
                  "    testApplicationId \"com.example.myapplication.test\"\n" +
                  "    testFunctionalTest false\n" +
                  "    testHandleProfiling true\n" +
                  "    testInstrumentationRunner \"abcd\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "    useJack false\n" +
                  "    versionCode 1\n" +
                  "    versionName \"1.0\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig.applicationId().delete();
    defaultConfig.removeAllConsumerProguardFiles();
    defaultConfig.dimension().delete();
    defaultConfig.removeAllManifestPlaceholders();
    defaultConfig.maxSdkVersion().delete();
    defaultConfig.minSdkVersion().delete();
    defaultConfig.multiDexEnabled().delete();
    defaultConfig.removeAllProguardFiles();
    defaultConfig.removeAllResConfigs();
    defaultConfig.removeAllResValues();
    defaultConfig.targetSdkVersion().delete();
    defaultConfig.testApplicationId().delete();
    defaultConfig.testFunctionalTest().delete();
    defaultConfig.testHandleProfiling().delete();
    defaultConfig.testInstrumentationRunner().delete();
    defaultConfig.removeAllTestInstrumentationRunnerArguments();
    defaultConfig.useJack().delete();
    defaultConfig.versionCode().delete();
    defaultConfig.versionName().delete();

    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());

    buildModel.resetState();

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());
  }

  public void testEditAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "    dimension \"abcd\"\n" +
                  "    maxSdkVersion 23\n" +
                  "    minSdkVersion \"15\"\n" +
                  "    multiDexEnabled true\n" +
                  "    targetSdkVersion \"22\" \n" +
                  "    testApplicationId \"com.example.myapplication.test\"\n" +
                  "    testFunctionalTest false\n" +
                  "    testHandleProfiling true\n" +
                  "    testInstrumentationRunner \"abcd\"\n" +
                  "    useJack false\n" +
                  "    versionCode 1\n" +
                  "    versionName \"1.0\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig.applicationId().setValue("com.example.myapplication-1");
    defaultConfig.dimension().setValue("efgh");
    defaultConfig.maxSdkVersion().setValue(24);
    defaultConfig.minSdkVersion().setValue("16");
    defaultConfig.multiDexEnabled().setValue(false);
    defaultConfig.targetSdkVersion().setValue("23");
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test");
    defaultConfig.testFunctionalTest().setValue(true);
    defaultConfig.testHandleProfiling().setValue(false);
    defaultConfig.testInstrumentationRunner().setValue("efgh");
    defaultConfig.useJack().setValue(true);
    defaultConfig.versionCode().setValue("2");
    defaultConfig.versionName().setValue("2.0");

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());

    buildModel.resetState();

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    // Test the fields that also accept an integer value along with the String value.
    defaultConfig.minSdkVersion().setValue(16);
    defaultConfig.targetSdkVersion().setValue(23);
    defaultConfig.versionCode().setValue(2);

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());

    buildModel.resetState();

    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
  }

  public void testAddAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());

    defaultConfig.applicationId().setValue("com.example.myapplication-1");
    defaultConfig.dimension().setValue("efgh");
    defaultConfig.maxSdkVersion().setValue(24);
    defaultConfig.minSdkVersion().setValue("16");
    defaultConfig.multiDexEnabled().setValue(false);
    defaultConfig.targetSdkVersion().setValue("23");
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test");
    defaultConfig.testFunctionalTest().setValue(true);
    defaultConfig.testHandleProfiling().setValue(false);
    defaultConfig.testInstrumentationRunner().setValue("efgh");
    defaultConfig.useJack().setValue(true);
    defaultConfig.versionCode().setValue("2");
    defaultConfig.versionName().setValue("2.0");

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());

    buildModel.resetState();

    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());

    // Test the fields that also accept an integer value along with the String valye.
    defaultConfig.minSdkVersion().setValue(16);
    defaultConfig.targetSdkVersion().setValue(23);
    defaultConfig.versionCode().setValue(2);

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());

    buildModel.resetState();

    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
  }

  public void testReplaceAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig.replaceConsumerProguardFile("proguard-android.txt", "proguard-android-1.txt");
    defaultConfig.replaceProguardFile("proguard-android.txt", "proguard-android-1.txt");
    defaultConfig.replaceResConfig("abcd", "xyz");
    defaultConfig.replaceResValue(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("abcd", "mnop", "qrst"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "mnop", "qrst")), defaultConfig.resValues());

    buildModel.resetState();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
  }

  public void testAddAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());

    defaultConfig.addConsumerProguardFile("proguard-android.txt");
    defaultConfig.addProguardFile("proguard-android.txt");
    defaultConfig.addResConfig("abcd");
    defaultConfig.addResValue(new ResValueImpl("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), defaultConfig.resValues());

    buildModel.resetState();

    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
  }

  public void testAddToAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig.addConsumerProguardFile("proguard-android-1.txt");
    defaultConfig.addProguardFile("proguard-android-1.txt");
    defaultConfig.addResConfig("xyz");
    defaultConfig.addResValue(new ResValueImpl("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    buildModel.resetState();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
  }

  public void testRemoveFromAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "    resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    defaultConfig.removeConsumerProguardFile("proguard-rules.pro");
    defaultConfig.removeProguardFile("proguard-rules.pro");
    defaultConfig.removeResConfig("efgh");
    defaultConfig.removeResValue(new ResValueImpl("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    buildModel.resetState();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());
  }

  public void testSetAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders key1:\"value1\", key2:\"value2\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    defaultConfig.setManifestPlaceholder("key1", 12345);
    defaultConfig.setManifestPlaceholder("key3", true);
    defaultConfig.setTestInstrumentationRunnerArgument("size", "small");
    defaultConfig.setTestInstrumentationRunnerArgument("key", "value");

    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "foo", "bar", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());

    buildModel.resetState();

    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testAddAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());

    defaultConfig.setManifestPlaceholder("activityLabel1", "newName1");
    defaultConfig.setManifestPlaceholder("activityLabel2", "newName2");
    defaultConfig.setTestInstrumentationRunnerArgument("size", "small");
    defaultConfig.setTestInstrumentationRunnerArgument("key", "value");

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());

    buildModel.resetState();

    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testRemoveAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    defaultConfig.removeManifestPlaceholder("activityLabel1");
    defaultConfig.removeTestInstrumentationRunnerArgument("size");

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    buildModel.resetState();

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testRemoveAndApplyElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    dimension \"abcd\"\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    maxSdkVersion 23\n" +
                  "    minSdkVersion 15\n" +
                  "    multiDexEnabled true\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "    targetSdkVersion 22 \n" +
                  "    testApplicationId \"com.example.myapplication.test\"\n" +
                  "    testFunctionalTest false\n" +
                  "    testHandleProfiling true\n" +
                  "    testInstrumentationRunner \"abcd\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "    useJack false\n" +
                  "    versionCode 1\n" +
                  "    versionName \"1.0\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    checkForValidPsiElement(defaultConfig, ProductFlavorModelImpl.class);

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig.applicationId().delete();
    defaultConfig.removeAllConsumerProguardFiles();
    defaultConfig.dimension().delete();
    defaultConfig.removeAllManifestPlaceholders();
    defaultConfig.maxSdkVersion().delete();
    defaultConfig.minSdkVersion().delete();
    defaultConfig.multiDexEnabled().delete();
    defaultConfig.removeAllProguardFiles();
    defaultConfig.removeAllResConfigs();
    defaultConfig.removeAllResValues();
    defaultConfig.targetSdkVersion().delete();
    defaultConfig.testApplicationId().delete();
    defaultConfig.testFunctionalTest().delete();
    defaultConfig.testHandleProfiling().delete();
    defaultConfig.testInstrumentationRunner().delete();
    defaultConfig.removeAllTestInstrumentationRunnerArguments();
    defaultConfig.useJack().delete();
    defaultConfig.versionCode().delete();
    defaultConfig.versionName().delete();

    checkForValidPsiElement(android, AndroidModelImpl.class);
    checkForValidPsiElement(defaultConfig, ProductFlavorModelImpl.class);

    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());

    applyChanges(buildModel);
    checkForInValidPsiElement(android, AndroidModelImpl.class);
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);
    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class);
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl.class);
    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());
  }

  public void testEditAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    applicationId \"com.example.myapplication\"\n" +
                  "    dimension \"abcd\"\n" +
                  "    maxSdkVersion 23\n" +
                  "    minSdkVersion \"15\"\n" +
                  "    multiDexEnabled true\n" +
                  "    targetSdkVersion \"22\" \n" +
                  "    testApplicationId \"com.example.myapplication.test\"\n" +
                  "    testFunctionalTest false\n" +
                  "    testHandleProfiling true\n" +
                  "    testInstrumentationRunner \"abcd\"\n" +
                  "    useJack false\n" +
                  "    versionCode 1\n" +
                  "    versionName \"1.0\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", 1, defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig.applicationId().setValue("com.example.myapplication-1");
    defaultConfig.dimension().setValue("efgh");
    defaultConfig.maxSdkVersion().setValue(24);
    defaultConfig.minSdkVersion().setValue("16");
    defaultConfig.multiDexEnabled().setValue(false);
    defaultConfig.targetSdkVersion().setValue("23");
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test");
    defaultConfig.testFunctionalTest().setValue(true);
    defaultConfig.testHandleProfiling().setValue(false);
    defaultConfig.testInstrumentationRunner().setValue("efgh");
    defaultConfig.useJack().setValue(true);
    defaultConfig.versionCode().setValue("2");
    defaultConfig.versionName().setValue("2.0");

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());

    applyChanges(buildModel);
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());
  }

  public void testEditAndApplyIntegerLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    minSdkVersion \"15\"\n" +
                  "    targetSdkVersion \"22\" \n" +
                  "    versionCode 1\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 1, defaultConfig.versionCode());

    defaultConfig.minSdkVersion().setValue(16);
    defaultConfig.targetSdkVersion().setValue(23);
    defaultConfig.versionCode().setValue(2);

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());

    applyChanges(buildModel);
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());
  }

  public void testAddAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertMissingProperty("dimension", defaultConfig.dimension());
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion());
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId());
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertMissingProperty("useJack", defaultConfig.useJack());
    assertMissingProperty("versionCode", defaultConfig.versionCode());
    assertMissingProperty("versionName", defaultConfig.versionName());

    defaultConfig.applicationId().setValue("com.example.myapplication-1");
    defaultConfig.dimension().setValue("efgh");
    defaultConfig.maxSdkVersion().setValue(24);
    defaultConfig.minSdkVersion().setValue("16");
    defaultConfig.multiDexEnabled().setValue(false);
    defaultConfig.targetSdkVersion().setValue("23");
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test");
    defaultConfig.testFunctionalTest().setValue(true);
    defaultConfig.testHandleProfiling().setValue(false);
    defaultConfig.testInstrumentationRunner().setValue("efgh");
    defaultConfig.useJack().setValue(true);
    defaultConfig.versionCode().setValue("2");
    defaultConfig.versionName().setValue("2.0");

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());

    applyChanges(buildModel);
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.FALSE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
    assertEquals("versionName", "2.0", defaultConfig.versionName());
  }

  public void testAddAndApplyIntegerLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertMissingProperty("applicationId", defaultConfig.applicationId());
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertMissingProperty("versionCode", defaultConfig.versionCode());

    defaultConfig.minSdkVersion().setValue(16);
    defaultConfig.targetSdkVersion().setValue(23);
    defaultConfig.versionCode().setValue(2);

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());

    applyChanges(buildModel);
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion());
    assertEquals("versionCode", 2, defaultConfig.versionCode());
  }

  public void testReplaceAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig.replaceConsumerProguardFile("proguard-android.txt", "proguard-android-1.txt");
    defaultConfig.replaceProguardFile("proguard-android.txt", "proguard-android-1.txt");
    defaultConfig.replaceResConfig("abcd", "xyz");
    defaultConfig.replaceResValue(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("abcd", "mnop", "qrst"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "mnop", "qrst")), defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "mnop", "qrst")), defaultConfig.resValues());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "mnop", "qrst")), defaultConfig.resValues());
  }

  public void testAddAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());

    defaultConfig.addConsumerProguardFile("proguard-android.txt");
    defaultConfig.addProguardFile("proguard-android.txt");
    defaultConfig.addProguardFile("proguard-rules.pro");
    defaultConfig.addResConfig("abcd");
    defaultConfig.addResValue(new ResValueImpl("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), defaultConfig.resValues());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), defaultConfig.resValues());
  }

  public void testAddToAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig.addConsumerProguardFile("proguard-android-1.txt");
    defaultConfig.addProguardFile("proguard-android-1.txt");
    defaultConfig.addResConfig("xyz");
    defaultConfig.addResValue(new ResValueImpl("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());
  }

  public void testRemoveFromAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "    resConfigs \"abcd\", \"efgh\"\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "    resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl"), new ResValueImpl("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    defaultConfig.removeConsumerProguardFile("proguard-rules.pro");
    defaultConfig.removeProguardFile("proguard-rules.pro");
    defaultConfig.removeResConfig("efgh");
    defaultConfig.removeResValue(new ResValueImpl("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("abcd", "efgh", "ijkl")), defaultConfig.resValues());
  }

  public void testRemoveFromAndApplyListElementsWithSingleElement() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'proguard-android.txt'\n" +
                  "    proguardFiles = ['proguard-rules.pro']\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-rules.pro"), defaultConfig.proguardFiles());

    defaultConfig.removeConsumerProguardFile("proguard-android.txt");
    defaultConfig.removeProguardFile("proguard-rules.pro");

    assertThat(defaultConfig.consumerProguardFiles()).named("consumerProguardFiles").isEmpty();
    assertThat(defaultConfig.proguardFiles()).named("proguardFiles").isEmpty();

    applyChanges(buildModel);
    assertThat(defaultConfig.consumerProguardFiles()).named("consumerProguardFiles").isEmpty();
    assertThat(defaultConfig.proguardFiles()).named("proguardFiles").isEmpty();

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    assertNull(android.defaultConfig().consumerProguardFiles());
    assertSize(0, android.defaultConfig().proguardFiles());
  }

  public void testSetAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders key1:\"value1\", key2:\"value2\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    defaultConfig.setManifestPlaceholder("key1", 12345);
    defaultConfig.setManifestPlaceholder("key3", true);
    defaultConfig.setTestInstrumentationRunnerArgument("size", "small");
    defaultConfig.setTestInstrumentationRunnerArgument("key", "value");

    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "foo", "bar", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "foo", "bar", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "foo", "bar", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testAddAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());

    defaultConfig.setManifestPlaceholder("activityLabel1", "newName1");
    defaultConfig.setManifestPlaceholder("activityLabel2", "newName2");
    defaultConfig.setTestInstrumentationRunnerArgument("size", "small");
    defaultConfig.setTestInstrumentationRunnerArgument("key", "value");

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "small", "key", "value"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testRemoveAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    defaultConfig.removeManifestPlaceholder("activityLabel1");
    defaultConfig.removeTestInstrumentationRunnerArgument("size");

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    defaultConfig = android.defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  private static final String NATIVE_ELEMENTS_TEXT = "android {\n" +
                                                     "  defaultConfig {\n" +
                                                     "    externalNativeBuild {\n" +
                                                     "      cmake {\n" +
                                                     "        abiFilters 'abiFilter1', 'abiFilter2'\n" +
                                                     "        arguments 'argument1', 'argument2'\n" +
                                                     "        cFlags 'cFlag1', 'cFlag2'\n" +
                                                     "        cppFlags 'cppFlag1', 'cppFlag2'\n" +
                                                     "        targets 'target1', 'target2'\n" +
                                                     "      }\n" +
                                                     "      ndkBuild {\n" +
                                                     "        abiFilters 'abiFilter3', 'abiFilter4'\n" +
                                                     "        arguments 'argument3', 'argument4'\n" +
                                                     "        cFlags 'cFlag3', 'cFlag4'\n" +
                                                     "        cppFlags 'cppFlag3', 'cppFlag4'\n" +
                                                     "        targets 'target3', 'target4'\n" +
                                                     "      }\n" +
                                                     "    }\n" +
                                                     "    ndk {\n" +
                                                     "      abiFilter 'abiFilter5'\n" +
                                                     "      abiFilters 'abiFilter6', 'abiFilter7'\n" +
                                                     "    }\n" +
                                                     "  }\n" +
                                                     "}";

  public void testParseNativeElements() throws Exception {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT);
    verifyNativeElements();
  }

  public void testEditNativeElements() throws Exception {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT);
    verifyNativeElements();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    cmake
      .replaceAbiFilter("abiFilter2", "abiFilterX")
      .replaceArgument("argument2", "argumentX")
      .replaceCFlag("cFlag2", "cFlagX")
      .replaceCppFlag("cppFlag2", "cppFlagX")
      .replaceTarget("target2", "targetX");

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    ndkBuild
      .replaceAbiFilter("abiFilter4", "abiFilterY")
      .replaceArgument("argument4", "argumentY")
      .replaceCFlag("cFlag4", "cFlagY")
      .replaceCppFlag("cppFlag4", "cppFlagY")
      .replaceTarget("target4", "targetY");

    NdkOptionsModel ndk = defaultConfig.ndk();
    ndk.replaceAbiFilter("abiFilter6", "abiFilterZ");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    defaultConfig = android.defaultConfig();

    externalNativeBuild = defaultConfig.externalNativeBuild();
    cmake = externalNativeBuild.cmake();
    assertEquals("cmake-abiFilters", ImmutableList.of("abiFilter1", "abiFilterX"), cmake.abiFilters());
    assertEquals("cmake-arguments", ImmutableList.of("argument1", "argumentX"), cmake.arguments());
    assertEquals("cmake-cFlags", ImmutableList.of("cFlag1", "cFlagX"), cmake.cFlags());
    assertEquals("cmake-cppFlags", ImmutableList.of("cppFlag1", "cppFlagX"), cmake.cppFlags());
    assertEquals("cmake-targets", ImmutableList.of("target1", "targetX"), cmake.targets());

    ndkBuild = externalNativeBuild.ndkBuild();
    assertEquals("ndkBuild-abiFilters", ImmutableList.of("abiFilter3", "abiFilterY"), ndkBuild.abiFilters());
    assertEquals("ndkBuild-arguments", ImmutableList.of("argument3", "argumentY"), ndkBuild.arguments());
    assertEquals("ndkBuild-cFlags", ImmutableList.of("cFlag3", "cFlagY"), ndkBuild.cFlags());
    assertEquals("ndkBuild-cppFlags", ImmutableList.of("cppFlag3", "cppFlagY"), ndkBuild.cppFlags());
    assertEquals("ndkBuild-targets", ImmutableList.of("target3", "targetY"), ndkBuild.targets());

    ndk = defaultConfig.ndk();
    assertEquals("ndk-abiFilters", ImmutableList.of("abiFilter5", "abiFilterZ", "abiFilter7"), ndk.abiFilters());
  }

  public void testAddNativeElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    verifyNullNativeElements();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    cmake
      .addAbiFilter("abiFilterX")
      .addArgument("argumentX")
      .addCFlag("cFlagX")
      .addCppFlag("cppFlagX")
      .addTarget("targetX");

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    ndkBuild
      .addAbiFilter("abiFilterY")
      .addArgument("argumentY")
      .addCFlag("cFlagY")
      .addCppFlag("cppFlagY")
      .addTarget("targetY");

    NdkOptionsModel ndk = defaultConfig.ndk();
    ndk.addAbiFilter("abiFilterZ");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    defaultConfig = android.defaultConfig();

    externalNativeBuild = defaultConfig.externalNativeBuild();
    cmake = externalNativeBuild.cmake();
    assertEquals("cmake-abiFilters", ImmutableList.of("abiFilterX"), cmake.abiFilters());
    assertEquals("cmake-arguments", ImmutableList.of("argumentX"), cmake.arguments());
    assertEquals("cmake-cFlags", ImmutableList.of("cFlagX"), cmake.cFlags());
    assertEquals("cmake-cppFlags", ImmutableList.of("cppFlagX"), cmake.cppFlags());
    assertEquals("cmake-targets", ImmutableList.of("targetX"), cmake.targets());

    ndkBuild = externalNativeBuild.ndkBuild();
    assertEquals("ndkBuild-abiFilters", ImmutableList.of("abiFilterY"), ndkBuild.abiFilters());
    assertEquals("ndkBuild-arguments", ImmutableList.of("argumentY"), ndkBuild.arguments());
    assertEquals("ndkBuild-cFlags", ImmutableList.of("cFlagY"), ndkBuild.cFlags());
    assertEquals("ndkBuild-cppFlags", ImmutableList.of("cppFlagY"), ndkBuild.cppFlags());
    assertEquals("ndkBuild-targets", ImmutableList.of("targetY"), ndkBuild.targets());

    ndk = defaultConfig.ndk();
    assertEquals("ndk-abiFilters", ImmutableList.of("abiFilterZ"), ndk.abiFilters());
  }

  public void testRemoveNativeElements() throws Exception {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT);
    verifyNativeElements();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    cmake
      .removeAllAbiFilters()
      .removeAllArguments()
      .removeAllCFlags()
      .removeAllCppFlags()
      .removeAllTargets();

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    ndkBuild
      .removeAllAbiFilters()
      .removeAllArguments()
      .removeAllCFlags()
      .removeAllCppFlags()
      .removeAllTargets();

    NdkOptionsModel ndk = defaultConfig.ndk();
    ndk.removeAllAbiFilters();

    applyChangesAndReparse(buildModel);
    verifyNullNativeElements();
  }

  public void testRemoveOneOfNativeElementsInTheList() throws Exception {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT);
    verifyNativeElements();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    cmake
      .removeAbiFilter("abiFilter1")
      .removeArgument("argument1")
      .removeCFlag("cFlag1")
      .removeCppFlag("cppFlag1")
      .removeTarget("target1");

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    ndkBuild
      .removeAbiFilter("abiFilter3")
      .removeArgument("argument3")
      .removeCFlag("cFlag3")
      .removeCppFlag("cppFlag3")
      .removeTarget("target3");

    NdkOptionsModel ndk = defaultConfig.ndk();
    ndk.removeAbiFilter("abiFilter6");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    defaultConfig = android.defaultConfig();

    externalNativeBuild = defaultConfig.externalNativeBuild();
    cmake = externalNativeBuild.cmake();
    assertEquals("cmake-abiFilters", ImmutableList.of("abiFilter2"), cmake.abiFilters());
    assertEquals("cmake-arguments", ImmutableList.of("argument2"), cmake.arguments());
    assertEquals("cmake-cFlags", ImmutableList.of("cFlag2"), cmake.cFlags());
    assertEquals("cmake-cppFlags", ImmutableList.of("cppFlag2"), cmake.cppFlags());
    assertEquals("cmake-targets", ImmutableList.of("target2"), cmake.targets());

    ndkBuild = externalNativeBuild.ndkBuild();
    assertEquals("ndkBuild-abiFilters", ImmutableList.of("abiFilter4"), ndkBuild.abiFilters());
    assertEquals("ndkBuild-arguments", ImmutableList.of("argument4"), ndkBuild.arguments());
    assertEquals("ndkBuild-cFlags", ImmutableList.of("cFlag4"), ndkBuild.cFlags());
    assertEquals("ndkBuild-cppFlags", ImmutableList.of("cppFlag4"), ndkBuild.cppFlags());
    assertEquals("ndkBuild-targets", ImmutableList.of("target4"), ndkBuild.targets());

    ndk = defaultConfig.ndk();
    assertEquals("ndk-abiFilters", ImmutableList.of("abiFilter5", "abiFilter7"), ndk.abiFilters());
  }

  public void testRemoveOnlyNativeElementInTheList() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    externalNativeBuild {\n" +
                  "      cmake {\n" +
                  "        abiFilters 'abiFilterX'\n" +
                  "        arguments 'argumentX'\n" +
                  "        cFlags 'cFlagX'\n" +
                  "        cppFlags 'cppFlagX'\n" +
                  "        targets 'targetX'\n" +
                  "      }\n" +
                  "      ndkBuild {\n" +
                  "        abiFilters 'abiFilterY'\n" +
                  "        arguments 'argumentY'\n" +
                  "        cFlags 'cFlagY'\n" +
                  "        cppFlags 'cppFlagY'\n" +
                  "        targets 'targetY'\n" +
                  "      }\n" +
                  "    }\n" +
                  "    ndk {\n" +
                  "      abiFilters 'abiFilterZ'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    assertEquals("cmake-abiFilters", ImmutableList.of("abiFilterX"), cmake.abiFilters());
    assertEquals("cmake-arguments", ImmutableList.of("argumentX"), cmake.arguments());
    assertEquals("cmake-cFlags", ImmutableList.of("cFlagX"), cmake.cFlags());
    assertEquals("cmake-cppFlags", ImmutableList.of("cppFlagX"), cmake.cppFlags());
    assertEquals("cmake-targets", ImmutableList.of("targetX"), cmake.targets());

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    assertEquals("ndkBuild-abiFilters", ImmutableList.of("abiFilterY"), ndkBuild.abiFilters());
    assertEquals("ndkBuild-arguments", ImmutableList.of("argumentY"), ndkBuild.arguments());
    assertEquals("ndkBuild-cFlags", ImmutableList.of("cFlagY"), ndkBuild.cFlags());
    assertEquals("ndkBuild-cppFlags", ImmutableList.of("cppFlagY"), ndkBuild.cppFlags());
    assertEquals("ndkBuild-targets", ImmutableList.of("targetY"), ndkBuild.targets());

    NdkOptionsModel ndk = defaultConfig.ndk();
    assertEquals("ndk-abiFilters", ImmutableList.of("abiFilterZ"), ndk.abiFilters());

    cmake
      .removeAbiFilter("abiFilterX")
      .removeArgument("argumentX")
      .removeCFlag("cFlagX")
      .removeCppFlag("cppFlagX")
      .removeTarget("targetX");

    ndkBuild
      .removeAbiFilter("abiFilterY")
      .removeArgument("argumentY")
      .removeCFlag("cFlagY")
      .removeCppFlag("cppFlagY")
      .removeTarget("targetY");

    ndk.removeAbiFilter("abiFilterZ");

    applyChangesAndReparse(buildModel);
    verifyNullNativeElements();
  }

  private void verifyNativeElements() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    assertEquals("cmake-abiFilters", ImmutableList.of("abiFilter1", "abiFilter2"), cmake.abiFilters());
    assertEquals("cmake-arguments", ImmutableList.of("argument1", "argument2"), cmake.arguments());
    assertEquals("cmake-cFlags", ImmutableList.of("cFlag1", "cFlag2"), cmake.cFlags());
    assertEquals("cmake-cppFlags", ImmutableList.of("cppFlag1", "cppFlag2"), cmake.cppFlags());
    assertEquals("cmake-targets", ImmutableList.of("target1", "target2"), cmake.targets());

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    assertEquals("ndkBuild-abiFilters", ImmutableList.of("abiFilter3", "abiFilter4"), ndkBuild.abiFilters());
    assertEquals("ndkBuild-arguments", ImmutableList.of("argument3", "argument4"), ndkBuild.arguments());
    assertEquals("ndkBuild-cFlags", ImmutableList.of("cFlag3", "cFlag4"), ndkBuild.cFlags());
    assertEquals("ndkBuild-cppFlags", ImmutableList.of("cppFlag3", "cppFlag4"), ndkBuild.cppFlags());
    assertEquals("ndkBuild-targets", ImmutableList.of("target3", "target4"), ndkBuild.targets());

    NdkOptionsModel ndk = defaultConfig.ndk();
    assertEquals("ndk-abiFilters", ImmutableList.of("abiFilter5", "abiFilter6", "abiFilter7"), ndk.abiFilters());
  }

  private void verifyNullNativeElements() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    ExternalNativeBuildOptionsModel externalNativeBuild = defaultConfig.externalNativeBuild();
    CMakeOptionsModel cmake = externalNativeBuild.cmake();
    assertNull("cmake-abiFilters", cmake.abiFilters());
    assertNull("cmake-arguments", cmake.arguments());
    assertNull("cmake-cFlags", cmake.cFlags());
    assertNull("cmake-cppFlags", cmake.cppFlags());
    assertNull("cmake-targets", cmake.targets());
    checkForInValidPsiElement(cmake, CMakeOptionsModelImpl.class);

    NdkBuildOptionsModel ndkBuild = externalNativeBuild.ndkBuild();
    assertNull("ndkBuild-abiFilters", ndkBuild.abiFilters());
    assertNull("ndkBuild-arguments", ndkBuild.arguments());
    assertNull("ndkBuild-cFlags", ndkBuild.cFlags());
    assertNull("ndkBuild-cppFlags", ndkBuild.cppFlags());
    assertNull("ndkBuild-targets", ndkBuild.targets());
    checkForInValidPsiElement(ndkBuild, NdkBuildOptionsModelImpl.class);

    NdkOptionsModel ndk = defaultConfig.ndk();
    assertNull("ndk-abiFilters", ndk.abiFilters());
    checkForInValidPsiElement(ndk, NdkOptionsModelImpl.class);
  }

  public void testRemoveNativeBlockElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    externalNativeBuild {\n" +
                  "    }\n" +
                  "    ndk {\n" +
                  "    }\n" +
                  "  }\n" +
                  "";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();
    checkForValidPsiElement(defaultConfig.externalNativeBuild(), ExternalNativeBuildOptionsModelImpl.class);
    checkForValidPsiElement(defaultConfig.ndk(), NdkOptionsModelImpl.class);

    defaultConfig.removeExternalNativeBuild();
    defaultConfig.removeNdk();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    defaultConfig = android.defaultConfig();
    checkForInValidPsiElement(defaultConfig.externalNativeBuild(), ExternalNativeBuildOptionsModelImpl.class);
    checkForInValidPsiElement(defaultConfig.ndk(), NdkOptionsModelImpl.class);
  }

  public void testRemoveExternalNativeBlockElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    externalNativeBuild {\n" +
                  "      cmake {\n" +
                  "      }\n" +
                  "      ndkBuild {\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    ExternalNativeBuildOptionsModel externalNativeBuild = android.defaultConfig().externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeOptionsModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildOptionsModelImpl.class);

    externalNativeBuild.removeCMake();
    externalNativeBuild.removeNdkBuild();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    externalNativeBuild = android.defaultConfig().externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeOptionsModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildOptionsModelImpl.class);
  }

  public void testFunctionCallWithParentheses() throws Exception {
    String text =  "android {\n" +
                     "defaultConfig {\n" +
                        "applicationId \"com.example.psd.sample.app.default\"\n" +
                        "testApplicationId \"com.example.psd.sample.app.default.test\"\n" +
                        "maxSdkVersion 26\n" +
                        "minSdkVersion 9\n" +
                        "targetSdkVersion(19)\n" +
                        "versionCode 1\n" +
                       "versionName \"1.0\" \n" +
                     "}\n" +
                   "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertEquals("targetSdkVersion", 19, defaultConfig.targetSdkVersion());
  }
}
