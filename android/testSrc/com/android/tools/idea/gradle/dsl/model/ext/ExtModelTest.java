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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;

/**
 * Tests for {@link ExtModel}.
 */
public class ExtModelTest extends GradleFileModelTestCase {

  public void testParsingSimplePropertyPerLine() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "ext.srcDirName = 'src/java'";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    String srcDirName = extModel.getProperty("srcDirName", String.class);
    assertNotNull(srcDirName);
    assertEquals("src/java", srcDirName);
  }

  public void testParsingSimplePropertyInExtBlock() throws IOException {
    String text = "ext {\n" +
                  "   COMPILE_SDK_VERSION = 21\n" +
                  "   srcDirName = 'src/java'\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    String srcDirName = extModel.getProperty("srcDirName", String.class);
    assertNotNull(srcDirName);
    assertEquals("src/java", srcDirName);
  }

  public void testParsingListOfProperties() throws IOException {
    String text = "ext.libraries = [\n" +
                  "    guava: \"com.google.guava:guava:19.0-rc1\",\n" +
                  "    design :  \"com.android.support:design:22.2.1\"\n" +
                  "]";
    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();

    String guavaLibrary = extModel.getProperty("libraries.guava", String.class);
    assertNotNull(guavaLibrary);
    assertEquals("com.google.guava:guava:19.0-rc1", guavaLibrary);
  }

  public void testResolveExtProperty() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "android {\n" +
                  "  compileSdkVersion COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());
  }

  public void testResolveQualifiedExtProperty() throws IOException {
    String text = "ext.constants = [\n" +
                  "  COMPILE_SDK_VERSION : 21\n" +
                  "]\n" +
                  "android {\n" +
                  "  compileSdkVersion constants.COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();

    Integer compileSdkVersion = extModel.getProperty("constants.COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());
  }

  public void testResolveMultiLevelExtProperty() throws IOException {
    String text = "ext.SDK_VERSION = 21\n" +
                  "ext.COMPILE_SDK_VERSION = SDK_VERSION\n" +
                  "android {\n" +
                  "  compileSdkVersion COMPILE_SDK_VERSION\n" +
                  "  defaultConfig {\n" +
                  "    targetSdkVersion compileSdkVersion\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    Integer sdkVersion = extModel.getProperty("SDK_VERSION", Integer.class);
    assertNotNull(sdkVersion);
    assertEquals(21, sdkVersion.intValue());

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    AndroidModel androidModel = buildModel.android();
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", "21", defaultConfig.targetSdkVersion());
  }

  public void testResolveMultiModuleExtProperty() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "ext.SDK_VERSION = 21";

    String subModuleText = "android {\n" +
                  "  compileSdkVersion SDK_VERSION\n" +
                  "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    Integer sdkVersion = extModel.getProperty("SDK_VERSION", Integer.class);
    assertNotNull(sdkVersion);
    assertEquals(21, sdkVersion.intValue());

    GradleBuildModel subModuleBuildModel = getSubModuleGradleBuildModel();
    ExtModel subModuleExtModel = subModuleBuildModel.ext();

    sdkVersion = subModuleExtModel.getProperty("SDK_VERSION", Integer.class);
    assertNull(sdkVersion); // SDK_VERSION is not defined in the sub module.

    AndroidModel androidModel = subModuleBuildModel.android();
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion()); // SDK_VERSION resolved from the main module.
  }

  public void testResolveVariablesInStringLiteral() throws IOException {
    String text = "ext.ANDROID = \"android\"\n" +
                  "ext.SDK_VERSION = 23\n" +
                  "android {\n" +
                  "  compileSdkVersion = \"$ANDROID-${SDK_VERSION}\"\n" +
                  "  defaultConfig {\n" +
                  "    targetSdkVersion \"$compileSdkVersion\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    String androidText = extModel.getProperty("ANDROID", String.class);
    assertNotNull(androidText);
    assertEquals("android", androidText);

    Integer sdkVersion = extModel.getProperty("SDK_VERSION", Integer.class);
    assertNotNull(sdkVersion);
    assertEquals(23, sdkVersion.intValue());

    AndroidModel androidModel = buildModel.android();
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", "android-23", defaultConfig.targetSdkVersion());
  }

  public void testResolveQualifiedVariableInStringLiteral() throws IOException {
    String text = "android {\n" +
                  "  compileSdkVersion 23\n" +
                  "  defaultConfig {\n" +
                  "    targetSdkVersion \"$android.compileSdkVersion\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    AndroidModel androidModel = buildModel.android();
    assertEquals("compileSdkVersion", "23", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
  }

  public void testStringReferenceInListProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles 'proguard-android.txt', TEST_STRING\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    assertEquals("test", extModel.getProperty("TEST_STRING", String.class));

    AndroidModel androidModel = buildModel.android();
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  public void testListReferenceInListProperty() throws IOException {
    String text = "ext.TEST_STRINGS = [\"test1\", \"test2\"]\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles TEST_STRINGS\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    GradleDslExpressionList expressionList = extModel.getProperty("TEST_STRINGS", GradleDslExpressionList.class);
    assertNotNull(expressionList);
    assertEquals(ImmutableList.of("test1", "test2"), expressionList.getValues(String.class));

    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("test1", "test2"), defaultConfig.proguardFiles());
  }

  public void testResolveVariableInListProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles 'proguard-android.txt', \"$TEST_STRING\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    assertEquals("test", extModel.getProperty("TEST_STRING", String.class));

    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  public void testStringReferenceInMapProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:TEST_STRING\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    assertEquals("test", extModel.getProperty("TEST_STRING", String.class));

    AndroidModel androidModel = buildModel.android();
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  // TODO: Support this use case to get this test pass.
  /*public void testMapReferenceInMapProperty() throws IOException {
    String text = "ext.TEST_MAP = [test1:\"value1\", test2:\"value2\"]\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments TEST_MAP\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel);

    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    GradleDslExpressionMap expressionMap = extModel.getProperty("TEST_MAP", GradleDslExpressionMap.class);
    assertNotNull(expressionMap);
    assertEquals(ImmutableMap.of("test1", "value1", "test2", "value2"), expressionMap.getValues(String.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertNotNull(defaultConfig);
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("test1", "value1", "test2", "value2"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }*/

  public void testResolveVariableInMapProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"$TEST_STRING\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    assertEquals("test", extModel.getProperty("TEST_STRING", String.class));

    ProductFlavorModel defaultConfig = buildModel.android().defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }
}