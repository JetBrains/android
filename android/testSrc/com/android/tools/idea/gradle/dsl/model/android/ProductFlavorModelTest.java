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
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModel.ResValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Tests for {@link ProductFlavorModel}.
 *
 * <p>Both {@code android.defaultConfig {}} and {@code android.productFlavors.xyz {}} uses the same structure with same attributes.
 * In this test, the product flavor structure defined by {@link ProductFlavorModel} is tested in great deal to cover all combinations using
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "1", defaultConfig.versionCode());
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
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
    assertEquals("versionCode", "1", defaultConfig.versionCode());
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.TRUE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.TRUE, defaultConfig.useJack());
    assertEquals("versionCode", "1", defaultConfig.versionCode());
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
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
    assertEquals("versionCode", "1", defaultConfig.versionCode());
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication1", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "efgh", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel3", "defaultName3", "activityLabel4", "defaultName4"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.FALSE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"), defaultConfig.proguardFiles());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test1", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key", "value"), defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap
      .of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2", "activityLabel3", "defaultName3", "activityLabel4",
          "defaultName4"), defaultConfig.manifestPlaceholders());
    assertEquals("proguardFiles", ImmutableList.of("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "ijkl", "mnop", "qrst"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
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

    ProductFlavorModel defaultConfig = getGradleBuildModel().android().defaultConfig();
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", "1", defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig.removeApplicationId();
    defaultConfig.removeAllConsumerProguardFiles();
    defaultConfig.removeDimension();
    defaultConfig.removeAllManifestPlaceholders();
    defaultConfig.removeMaxSdkVersion();
    defaultConfig.removeMinSdkVersion();
    defaultConfig.removeMultiDexEnabled();
    defaultConfig.removeAllProguardFiles();
    defaultConfig.removeAllResConfigs();
    defaultConfig.removeAllResValues();
    defaultConfig.removeTargetSdkVersion();
    defaultConfig.removeTestApplicationId();
    defaultConfig.removeTestFunctionalTest();
    defaultConfig.removeTestHandleProfiling();
    defaultConfig.removeTestInstrumentationRunner();
    defaultConfig.removeAllTestInstrumentationRunnerArguments();
    defaultConfig.removeUseJack();
    defaultConfig.removeVersionCode();
    defaultConfig.removeVersionName();

    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());

    buildModel.resetState();

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", "1", defaultConfig.versionCode());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
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
    assertEquals("versionCode", "1", defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig
      .setApplicationId("com.example.myapplication-1")
      .setDimension("efgh")
      .setMaxSdkVersion(24)
      .setMinSdkVersion("16")
      .setMultiDexEnabled(false)
      .setTargetSdkVersion("23")
      .setTestApplicationId("com.example.myapplication-1.test")
      .setTestFunctionalTest(true)
      .setTestHandleProfiling(false)
      .setTestInstrumentationRunner("efgh")
      .setUseJack(true)
      .setVersionCode("2")
      .setVersionName("2.0");

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
    assertEquals("versionCode", "1", defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    // Test the fields that also accept an integer value along with the String valye.
    defaultConfig.setMinSdkVersion(16)
      .setTargetSdkVersion(23)
      .setVersionCode(2);

    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());

    buildModel.resetState();

    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "1", defaultConfig.versionCode());
  }

  public void testAddAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());

    defaultConfig
      .setApplicationId("com.example.myapplication-1")
      .setDimension("efgh")
      .setMaxSdkVersion(24)
      .setMinSdkVersion("16")
      .setMultiDexEnabled(false)
      .setTargetSdkVersion("23")
      .setTestApplicationId("com.example.myapplication-1.test")
      .setTestFunctionalTest(true)
      .setTestHandleProfiling(false)
      .setTestInstrumentationRunner("efgh")
      .setUseJack(true)
      .setVersionCode("2")
      .setVersionName("2.0");

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

    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());

    // Test the fields that also accept an integer value along with the String valye.
    defaultConfig.setMinSdkVersion(16)
      .setTargetSdkVersion(23)
      .setVersionCode(2);

    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());

    buildModel.resetState();

    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("versionCode", defaultConfig.versionCode());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig
      .replaceConsumerProguardFile("proguard-android.txt", "proguard-android-1.txt")
      .replaceProguardFile("proguard-android.txt", "proguard-android-1.txt")
      .replaceResConfig("abcd", "xyz")
      .replaceResValue(new ResValue("abcd", "efgh", "ijkl"), new ResValue("abcd", "mnop", "qrst"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "mnop", "qrst")), defaultConfig.resValues());

    buildModel.resetState();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
  }

  public void testAddAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());

    defaultConfig
      .addConsumerProguardFile("proguard-android.txt")
      .addProguardFile("proguard-android.txt")
      .addResConfig("abcd")
      .addResValue(new ResValue("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("mnop", "qrst", "uvwx")), defaultConfig.resValues());

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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig
      .addConsumerProguardFile("proguard-android-1.txt")
      .addProguardFile("proguard-android-1.txt")
      .addResConfig("xyz")
      .addResValue(new ResValue("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    buildModel.resetState();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    defaultConfig
      .removeConsumerProguardFile("proguard-rules.pro")
      .removeProguardFile("proguard-rules.pro")
      .removeResConfig("efgh")
      .removeResValue(new ResValue("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    buildModel.resetState();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

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
    assertTrue(android.hasValidPsiElement());

    ProductFlavorModel defaultConfig = android.defaultConfig();
    assertTrue(defaultConfig.hasValidPsiElement());

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("dimension", "abcd", defaultConfig.dimension());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion());
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("multiDexEnabled", Boolean.TRUE, defaultConfig.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId());
    assertEquals("testFunctionalTest", Boolean.FALSE, defaultConfig.testFunctionalTest());
    assertEquals("testHandleProfiling", Boolean.TRUE, defaultConfig.testHandleProfiling());
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
    assertEquals("useJack", Boolean.FALSE, defaultConfig.useJack());
    assertEquals("versionCode", "1", defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig.removeApplicationId();
    defaultConfig.removeAllConsumerProguardFiles();
    defaultConfig.removeDimension();
    defaultConfig.removeAllManifestPlaceholders();
    defaultConfig.removeMaxSdkVersion();
    defaultConfig.removeMinSdkVersion();
    defaultConfig.removeMultiDexEnabled();
    defaultConfig.removeAllProguardFiles();
    defaultConfig.removeAllResConfigs();
    defaultConfig.removeAllResValues();
    defaultConfig.removeTargetSdkVersion();
    defaultConfig.removeTestApplicationId();
    defaultConfig.removeTestFunctionalTest();
    defaultConfig.removeTestHandleProfiling();
    defaultConfig.removeTestInstrumentationRunner();
    defaultConfig.removeAllTestInstrumentationRunnerArguments();
    defaultConfig.removeUseJack();
    defaultConfig.removeVersionCode();
    defaultConfig.removeVersionName();

    assertTrue(android.hasValidPsiElement());
    assertTrue(defaultConfig.hasValidPsiElement());
    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());

    applyChanges(buildModel);
    assertFalse(android.hasValidPsiElement());
    assertFalse(android.defaultConfig().hasValidPsiElement());
    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());

    buildModel.reparse();
    android = buildModel.android();
    assertFalse(android.hasValidPsiElement());
    assertFalse(android.defaultConfig().hasValidPsiElement());
    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("manifestPlaceholders", defaultConfig.manifestPlaceholders());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
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
    assertEquals("versionCode", "1", defaultConfig.versionCode());
    assertEquals("versionName", "1.0", defaultConfig.versionName());

    defaultConfig
      .setApplicationId("com.example.myapplication-1")
      .setDimension("efgh")
      .setMaxSdkVersion(24)
      .setMinSdkVersion("16")
      .setMultiDexEnabled(false)
      .setTargetSdkVersion("23")
      .setTestApplicationId("com.example.myapplication-1.test")
      .setTestFunctionalTest(true)
      .setTestHandleProfiling(false)
      .setTestInstrumentationRunner("efgh")
      .setUseJack(true)
      .setVersionCode("2")
      .setVersionName("2.0");

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
    defaultConfig = buildModel.android().defaultConfig();
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "1", defaultConfig.versionCode());

    defaultConfig.setMinSdkVersion(16)
      .setTargetSdkVersion(23)
      .setVersionCode(2);

    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());

    applyChanges(buildModel);
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());

    buildModel.reparse();
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
  }

  public void testAddAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("dimension", defaultConfig.dimension());
    assertNull("maxSdkVersion", defaultConfig.maxSdkVersion());
    assertNull("minSdkVersion", defaultConfig.minSdkVersion());
    assertNull("multiDexEnabled", defaultConfig.multiDexEnabled());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("testApplicationId", defaultConfig.testApplicationId());
    assertNull("testFunctionalTest", defaultConfig.testFunctionalTest());
    assertNull("testHandleProfiling", defaultConfig.testHandleProfiling());
    assertNull("testInstrumentationRunner", defaultConfig.testInstrumentationRunner());
    assertNull("useJack", defaultConfig.useJack());
    assertNull("versionCode", defaultConfig.versionCode());
    assertNull("versionName", defaultConfig.versionName());

    defaultConfig
      .setApplicationId("com.example.myapplication-1")
      .setDimension("efgh")
      .setMaxSdkVersion(24)
      .setMinSdkVersion("16")
      .setMultiDexEnabled(false)
      .setTargetSdkVersion("23")
      .setTestApplicationId("com.example.myapplication-1.test")
      .setTestFunctionalTest(true)
      .setTestHandleProfiling(false)
      .setTestInstrumentationRunner("efgh")
      .setUseJack(true)
      .setVersionCode("2")
      .setVersionName("2.0");

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
    defaultConfig = buildModel.android().defaultConfig();
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertNull("applicationId", defaultConfig.applicationId());
    assertNull("targetSdkVersion", defaultConfig.targetSdkVersion());
    assertNull("versionCode", defaultConfig.versionCode());

    defaultConfig.setMinSdkVersion(16)
      .setTargetSdkVersion(23)
      .setVersionCode(2);

    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());

    applyChanges(buildModel);
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());

    buildModel.reparse();
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion());
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
    assertEquals("versionCode", "2", defaultConfig.versionCode());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig
      .replaceConsumerProguardFile("proguard-android.txt", "proguard-android-1.txt")
      .replaceProguardFile("proguard-android.txt", "proguard-android-1.txt")
      .replaceResConfig("abcd", "xyz")
      .replaceResValue(new ResValue("abcd", "efgh", "ijkl"), new ResValue("abcd", "mnop", "qrst"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "mnop", "qrst")), defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "mnop", "qrst")), defaultConfig.resValues());

    buildModel.reparse();
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("xyz", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "mnop", "qrst")), defaultConfig.resValues());
  }

  public void testAddAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertNull("consumerProguardFiles", defaultConfig.consumerProguardFiles());
    assertNull("proguardFiles", defaultConfig.proguardFiles());
    assertNull("resConfigs", defaultConfig.resConfigs());
    assertNull("resValues", defaultConfig.resValues());

    defaultConfig
      .addConsumerProguardFile("proguard-android.txt")
      .addProguardFile("proguard-android.txt")
      .addProguardFile("proguard-rules.pro")
      .addResConfig("abcd")
      .addResValue(new ResValue("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("mnop", "qrst", "uvwx")), defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("mnop", "qrst", "uvwx")), defaultConfig.resValues());

    buildModel.reparse();
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("mnop", "qrst", "uvwx")), defaultConfig.resValues());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    defaultConfig
      .addConsumerProguardFile("proguard-android-1.txt")
      .addProguardFile("proguard-android-1.txt")
      .addResConfig("xyz")
      .addResValue(new ResValue("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    buildModel.reparse();
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh", "xyz"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd", "efgh"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl"), new ResValue("mnop", "qrst", "uvwx")),
                 defaultConfig.resValues());

    defaultConfig
      .removeConsumerProguardFile("proguard-rules.pro")
      .removeProguardFile("proguard-rules.pro")
      .removeResConfig("efgh")
      .removeResValue(new ResValue("mnop", "qrst", "uvwx"));

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());

    buildModel.reparse();
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.proguardFiles());
    assertEquals("resConfigs", ImmutableList.of("abcd"), defaultConfig.resConfigs());
    assertEquals("resValues", ImmutableList.of(new ResValue("abcd", "efgh", "ijkl")), defaultConfig.resValues());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-rules.pro"), defaultConfig.proguardFiles());

    defaultConfig
      .removeConsumerProguardFile("proguard-android.txt")
      .removeProguardFile("proguard-rules.pro");

    assertEquals("consumerProguardFiles", ImmutableList.of(), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of(), defaultConfig.proguardFiles());

    applyChanges(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of(), defaultConfig.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of(), defaultConfig.proguardFiles());

    buildModel.reparse();
    AndroidModel android = buildModel.android();
    assertFalse(android.hasValidPsiElement());
    assertFalse(android.defaultConfig().hasValidPsiElement());
    assertNull(android.defaultConfig().consumerProguardFiles());
    assertNull(android.defaultConfig().proguardFiles());
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
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
    defaultConfig = buildModel.android().defaultConfig();
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
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
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
    defaultConfig = buildModel.android().defaultConfig();
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

    final GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();

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
    defaultConfig = buildModel.android().defaultConfig();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("foo", "bar"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }
}
