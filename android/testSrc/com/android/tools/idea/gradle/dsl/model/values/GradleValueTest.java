/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.tools.idea.gradle.dsl.api.FlavorTypeModel.ResValue;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel.BuildConfigField;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.MAP_TYPE;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleValueImpl}.
 */
public class GradleValueTest extends GradleFileModelTestCase {
  public void testGradleValuesOfLiteralElementsInApplicationStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion 23\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    verifyPropertyModel(android.buildToolsVersion(), "android.buildToolsVersion", "23.0.0");
    verifyPropertyModel(android.compileSdkVersion(), "android.compileSdkVersion", "23");
    verifyPropertyModel(android.defaultPublishConfig(), "android.defaultPublishConfig", "debug");
    verifyPropertyModel(android.generatePureSplits(), "android.generatePureSplits", "true");
    verifyPropertyModel(android.publishNonDefault(), "android.publishNonDefault", "false");
    verifyPropertyModel(android.resourcePrefix(), "android.resourcePrefix", "abcd");
  }

  public void testGradleValuesOfLiteralElementsInAssignmentStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion = \"23.0.0\"\n" +
                  "  compileSdkVersion = \"android-23\"\n" +
                  "  defaultPublishConfig = \"debug\"\n" +
                  "  generatePureSplits = true\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    verifyPropertyModel(android.buildToolsVersion(), "android.buildToolsVersion", "23.0.0");
    verifyPropertyModel(android.compileSdkVersion(), "android.compileSdkVersion", "android-23");
    verifyPropertyModel(android.defaultPublishConfig(), "android.defaultPublishConfig", "debug");
    verifyPropertyModel(android.generatePureSplits(), "android.generatePureSplits", "true");
  }

  public void testListOfGradleValuesInApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'con-proguard-android.txt', 'con-proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs 'abcd', 'efgh'" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    List<GradlePropertyModel> consumerProguardFiles = defaultConfig.consumerProguardFiles().getValue(LIST_TYPE);
    assertNotNull(consumerProguardFiles);
    assertThat(consumerProguardFiles).hasSize(2);
    verifyPropertyModel(consumerProguardFiles.get(0), "android.defaultConfig.consumerProguardFiles[0]",
                        "con-proguard-android.txt");
    verifyPropertyModel(consumerProguardFiles.get(1), "android.defaultConfig.consumerProguardFiles[1]",
                        "con-proguard-rules.pro");

    List<GradlePropertyModel> proguardFiles = defaultConfig.proguardFiles().getValue(LIST_TYPE);
    assertNotNull(proguardFiles);
    assertThat(proguardFiles).hasSize(2);
    verifyPropertyModel(proguardFiles.get(0), "android.defaultConfig.proguardFiles[0]", "proguard-android.txt");
    verifyPropertyModel(proguardFiles.get(1), "android.defaultConfig.proguardFiles[1]", "proguard-rules.pro");

    List<GradlePropertyModel> resConfigs = defaultConfig.resConfigs().getValue(LIST_TYPE);
    assertNotNull(resConfigs);
    assertThat(resConfigs).hasSize(2);
    verifyPropertyModel(resConfigs.get(0), "android.defaultConfig.resConfigs[0]", "abcd");
    verifyPropertyModel(resConfigs.get(1), "android.defaultConfig.resConfigs[1]", "efgh");
  }

  public void testListOfGradleValuesInAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles = ['con-proguard-android.txt', 'con-proguard-rules.pro']\n" +
                  "    proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    List<GradlePropertyModel> consumerProguardFiles = defaultConfig.consumerProguardFiles().getValue(LIST_TYPE);
    assertNotNull(consumerProguardFiles);
    assertThat(consumerProguardFiles).hasSize(2);
    verifyPropertyModel(consumerProguardFiles.get(0), "android.defaultConfig.consumerProguardFiles[0]",
                        "con-proguard-android.txt");
    verifyPropertyModel(consumerProguardFiles.get(1), "android.defaultConfig.consumerProguardFiles[1]",
                        "con-proguard-rules.pro");

    List<GradlePropertyModel> proguardFiles = defaultConfig.proguardFiles().getValue(LIST_TYPE);
    assertNotNull(proguardFiles);
    assertThat(proguardFiles).hasSize(2);
    verifyPropertyModel(proguardFiles.get(0), "android.defaultConfig.proguardFiles[0]", "proguard-android.txt");
    verifyPropertyModel(proguardFiles.get(1), "android.defaultConfig.proguardFiles[1]", "proguard-rules.pro");
  }

  public void testMapOfGradleValuesInApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    Map<String, GradlePropertyModel> manifestPlaceholders = defaultConfig.manifestPlaceholders().getValue(MAP_TYPE);
    assertNotNull(manifestPlaceholders);
    GradlePropertyModel activityLabel1 = manifestPlaceholders.get("activityLabel1");
    assertNotNull(activityLabel1);
    verifyPropertyModel(activityLabel1, "android.defaultConfig.manifestPlaceholders.activityLabel1", "defaultName1");
    GradlePropertyModel activityLabel2 = manifestPlaceholders.get("activityLabel2");
    assertNotNull(activityLabel2);
    verifyPropertyModel(activityLabel2, "android.defaultConfig.manifestPlaceholders.activityLabel2", "defaultName2");

    Map<String, GradlePropertyModel> testInstrumentationRunnerArguments =
      defaultConfig.testInstrumentationRunnerArguments().getValue(MAP_TYPE);
    assertNotNull(testInstrumentationRunnerArguments);
    GradlePropertyModel size = testInstrumentationRunnerArguments.get("size");
    verifyPropertyModel(size, "android.defaultConfig.testInstrumentationRunnerArguments.size", "medium");
    GradlePropertyModel foo = testInstrumentationRunnerArguments.get("foo");
    verifyPropertyModel(foo, "android.defaultConfig.testInstrumentationRunnerArguments.foo", "bar");
  }

  public void testMapOfGradleValuesInAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "    testInstrumentationRunnerArguments = [size:\"medium\", foo:\"bar\"]\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    Map<String, GradlePropertyModel> manifestPlaceholders = defaultConfig.manifestPlaceholders().getValue(MAP_TYPE);
    assertNotNull(manifestPlaceholders);
    GradlePropertyModel activityLabel1 = manifestPlaceholders.get("activityLabel1");
    assertNotNull(activityLabel1);
    verifyPropertyModel(activityLabel1, "android.defaultConfig.manifestPlaceholders.activityLabel1", "defaultName1");
    GradlePropertyModel activityLabel2 = manifestPlaceholders.get("activityLabel2");
    assertNotNull(activityLabel2);
    verifyPropertyModel(activityLabel2, "android.defaultConfig.manifestPlaceholders.activityLabel2", "defaultName2");

    Map<String, GradlePropertyModel> testInstrumentationRunnerArguments =
      defaultConfig.testInstrumentationRunnerArguments().getValue(MAP_TYPE);
    assertNotNull(testInstrumentationRunnerArguments);
    GradlePropertyModel size = testInstrumentationRunnerArguments.get("size");
    verifyPropertyModel(size, "android.defaultConfig.testInstrumentationRunnerArguments.size", "medium");
    GradlePropertyModel foo = testInstrumentationRunnerArguments.get("foo");
    verifyPropertyModel(foo, "android.defaultConfig.testInstrumentationRunnerArguments.foo", "bar");
  }

  public void testGradleValuesOfTypeNameValueElements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    resValue \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "  }\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();

    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();
    List<ResValue> resValues = defaultConfig.resValues();
    assertNotNull(resValues);
    assertThat(resValues).hasSize(1);
    verifyListProperty(resValues.get(0).getModel(), "android.defaultConfig.resValue", Lists.newArrayList("abcd", "efgh", "ijkl"));

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(1);
    List<BuildConfigField> buildConfigFields = buildTypes.get(0).buildConfigFields();
    assertNotNull(buildConfigFields);
    assertThat(buildConfigFields).hasSize(1);
    verifyListProperty(buildConfigFields.get(0).getModel(), "android.buildTypes.xyz.buildConfigField",
                       Lists.newArrayList("mnop", "qrst", "uvwx"));
  }
}
