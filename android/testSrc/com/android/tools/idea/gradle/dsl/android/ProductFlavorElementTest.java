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

import com.android.tools.idea.gradle.dsl.android.ProductFlavorElement.ResValue;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModelParserTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Tests for {@link ProductFlavorElement}.
 *
 * <p>Both {@code android.defaultConfig {}} and {@code android.productFlavors.xyz {}} uses the same structure with same attributes.
 * In this test, the product flavor structure defined by {@link ProductFlavorElement} is tested in great deal to cover all combinations using
 * the {@code android.defaultConfig {}} block. The general structure of {@code android.productFlavors {}} is tested in
 * {@link ProductFlavorElementTest}.
 */
public class ProductFlavorElementTest extends GradleBuildModelParserTestCase {
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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

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

    AndroidElement android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorElement defaultConfig = android.defaultConfig();
    assertNotNull(defaultConfig);

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 defaultConfig.manifestPlaceholders());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }
}
