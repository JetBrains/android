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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.Map;

import static com.android.utils.FileUtils.toSystemIndependentPath;

/**
 * Tests for {@link ExtModel}.
 */
public class ExtModelTest extends GradleFileModelTestCase {

  public void testParsingSimplePropertyPerLine() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "ext.srcDirName = 'src/java'";

    writeToBuildFile(text);

    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals(21, extModel.getLiteralProperty("COMPILE_SDK_VERSION", Integer.class));
    assertEquals("src/java", extModel.getLiteralProperty("srcDirName", String.class));
  }

  public void testParsingSimplePropertyInExtBlock() throws IOException {
    String text = "ext {\n" +
                  "   COMPILE_SDK_VERSION = 21\n" +
                  "   srcDirName = 'src/java'\n" +
                  "}";

    writeToBuildFile(text);

    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals(21, extModel.getLiteralProperty("COMPILE_SDK_VERSION", Integer.class));
    assertEquals("src/java", extModel.getLiteralProperty("srcDirName", String.class));
  }

  public void testParsingListOfProperties() throws IOException {
    String text = "ext.libraries = [\n" +
                  "    guava: \"com.google.guava:guava:19.0-rc1\",\n" +
                  "    design :  \"com.android.support:design:22.2.1\"\n" +
                  "]";
    writeToBuildFile(text);

    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals("com.google.guava:guava:19.0-rc1", extModel.getLiteralProperty("libraries.guava", String.class));
  }

  public void testResolveExtProperty() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "android {\n" +
                  "  compileSdkVersion COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals(21, extModel.getLiteralProperty("COMPILE_SDK_VERSION", Integer.class));

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

    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals(21, extModel.getLiteralProperty("constants.COMPILE_SDK_VERSION", Integer.class));

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
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals(21, extModel.getLiteralProperty("SDK_VERSION", Integer.class));
    assertEquals(21, extModel.getLiteralProperty("COMPILE_SDK_VERSION", Integer.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
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
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals(21, extModel.getLiteralProperty("SDK_VERSION", Integer.class));

    GradleBuildModel subModuleBuildModel = getSubModuleGradleBuildModel();
    ExtModelImpl subModuleExtModel = (ExtModelImpl)getSubModuleGradleBuildModel().ext();
    assertNull(subModuleExtModel.getLiteralProperty("SDK_VERSION", Integer.class).value()); // SDK_VERSION is not defined in the sub module.

    AndroidModel androidModel = subModuleBuildModel.android();
    assertNotNull(androidModel);
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
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals("android", extModel.getLiteralProperty("ANDROID", String.class));
    assertEquals(23, extModel.getLiteralProperty("SDK_VERSION", Integer.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
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
    assertNotNull(androidModel);
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
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals("test", extModel.getLiteralProperty("TEST_STRING", String.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
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
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();

    GradleDslExpressionList testStringsList = extModel.getPropertyElement("TEST_STRINGS", GradleDslExpressionList.class);
    assertNotNull(testStringsList);
    assertEquals(ImmutableList.of("test1", "test2"), testStringsList.getValues(String.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("test1", "test2"), defaultConfig.proguardFiles());
  }

  public void testResolveVariableInListProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles 'proguard-android.txt', \"$TEST_STRING\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals("test", extModel.getLiteralProperty("TEST_STRING", String.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  public void testStringReferenceInMapProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:TEST_STRING\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals("test", extModel.getLiteralProperty("TEST_STRING", String.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
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

    GradleBuildModelImpl buildModel = getGradleBuildModel();
    assertNotNull(buildModel);

    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    GradleDslExpressionMap expressionMap = extModel.getProperty("TEST_MAP", GradleDslExpressionMap.class);
    assertNotNull(expressionMap);
    assertEquals(ImmutableMap.of("test1", "value1", "test2", "value2"), expressionMap.getValues(String.class));

    AndroidModelImpl androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModelImpl defaultConfig = androidModel.defaultConfig();
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
    ExtModelImpl extModel = (ExtModelImpl)getGradleBuildModel().ext();
    assertEquals("test", extModel.getLiteralProperty("TEST_STRING", String.class));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  public void testResolveVariableInSubModuleBuildFile() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";
    String mainModuleBuildText = "ext.xyz = \"value_from_main_module_build_file\"";
    String subModulePropertiesText = "xyz=value_from_sub_module_properties_file";
    String subModuleBuildText = "ext.xyz = \"value_from_sub_module_build_file\"\n" +
                                "ext.test = xyz";

    writeToSettingsFile(settingsText);
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModulePropertiesFile(subModulePropertiesText);
    writeToSubModuleBuildFile(subModuleBuildText);

    ExtModelImpl extModel = (ExtModelImpl)getSubModuleGradleBuildModel().ext();
    assertEquals("value_from_sub_module_build_file", extModel.getLiteralProperty("test", String.class));
  }

  public void testResolveVariableInSubModulePropertiesFile() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";
    String mainModuleBuildText = "ext.xyz = \"value_from_main_module_build_file\"";
    String subModulePropertiesText = "xyz=value_from_sub_module_properties_file";
    String subModuleBuildText = "ext.test = xyz";

    writeToSettingsFile(settingsText);
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModulePropertiesFile(subModulePropertiesText);
    writeToSubModuleBuildFile(subModuleBuildText);

    ExtModelImpl extModel = (ExtModelImpl)getSubModuleGradleBuildModel().ext();
    assertEquals("value_from_sub_module_properties_file", extModel.getLiteralProperty("test", String.class));
  }

  public void testResolveVariableInMainModulePropertiesFile() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";
    String mainModuleBuildText = "ext.xyz = \"value_from_main_module_build_file\"";
    String subModuleBuildText = "ext.test = xyz";

    writeToSettingsFile(settingsText);
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModuleBuildFile(subModuleBuildText);

    ExtModelImpl extModel = (ExtModelImpl)getSubModuleGradleBuildModel().ext();
    assertEquals("value_from_main_module_properties_file", extModel.getLiteralProperty("test", String.class));
  }

  public void testResolveVariableInMainModuleBuildFile() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModuleBuildText = "ext.xyz = \"value_from_main_module_build_file\"";
    String subModuleBuildText = "ext.test = xyz";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModuleBuildFile(subModuleBuildText);

    ExtModelImpl extModel = (ExtModelImpl)getSubModuleGradleBuildModel().ext();
    assertEquals("value_from_main_module_build_file", extModel.getLiteralProperty("test", String.class));
  }

  public void testResolveMultiLevelExtPropertyWithHistory() throws IOException {
    String text = "ext.FIRST = 123\n" +
                  "ext.SECOND = FIRST\n" +
                  "ext.THIRD = SECOND";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    GradleNullableValue<Integer> third = extModel.getLiteralProperty("THIRD", Integer.class);
    verifyGradleValue(third, "ext.THIRD", "SECOND");
    assertEquals(Integer.valueOf(123), third.value());
    Map<String, GradleNotNullValue<Object>> thirdResolvedVariables = third.getResolvedVariables();
    assertEquals(1, thirdResolvedVariables.size());

    GradleNotNullValue<Object> second = thirdResolvedVariables.get("SECOND");
    assertNotNull(second);
    verifyGradleValue(second, "ext.SECOND", "FIRST");
    assertEquals(Integer.valueOf(123), second.value());
    Map<String, GradleNotNullValue<Object>> secondResolvedVariables = second.getResolvedVariables();
    assertEquals(1, secondResolvedVariables.size());

    GradleNotNullValue<Object> first = secondResolvedVariables.get("FIRST");
    assertNotNull(first);
    verifyGradleValue(first, "ext.FIRST", "123");
    assertEquals(Integer.valueOf(123), first.value());
    assertEquals(0, first.getResolvedVariables().size());
  }

  public void testResolveMultiModuleExtPropertyWithHistory() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModuleText = "ext.FIRST = 123";
    String subModuleText = "ext.SECOND = FIRST";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    GradleBuildModel buildModel = getSubModuleGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    GradleNullableValue<Integer> second = extModel.getLiteralProperty("SECOND", Integer.class);
    verifyGradleValue(second, "ext.SECOND", "FIRST", toSystemIndependentPath(mySubModuleBuildFile.getPath()));
    assertEquals(Integer.valueOf(123), second.value());
    Map<String, GradleNotNullValue<Object>> secondResolvedVariables = second.getResolvedVariables();
    assertEquals(1, secondResolvedVariables.size());

    GradleNotNullValue<Object> first = secondResolvedVariables.get("FIRST");
    assertNotNull(first);
    verifyGradleValue(first, "ext.FIRST", "123");
    assertEquals(Integer.valueOf(123), first.value());
    assertEquals(0, first.getResolvedVariables().size());
  }

  public void testResolveMultiModuleExtPropertyFromPropertiesFileWithHistory() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModulePropertiesText = "first=value_from_gradle_properties";
    String mainModuleBuildText = "ext.second = first";
    String subModuleBuildText = "ext.third = second";

    writeToSettingsFile(settingsText);
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModuleBuildFile(subModuleBuildText);

    GradleBuildModel buildModel = getSubModuleGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    GradleNullableValue<String> third = extModel.getLiteralProperty("third", String.class);
    verifyGradleValue(third, "ext.third", "second", toSystemIndependentPath(mySubModuleBuildFile.getPath()));
    assertEquals("value_from_gradle_properties", third.value());
    Map<String, GradleNotNullValue<Object>> thirdResolvedVariables = third.getResolvedVariables();
    assertEquals(1, thirdResolvedVariables.size());

    GradleNotNullValue<Object> second = thirdResolvedVariables.get("second");
    assertNotNull(second);
    verifyGradleValue(second, "ext.second", "first", toSystemIndependentPath(myBuildFile.getPath()));
    assertEquals("value_from_gradle_properties", second.value());
    Map<String, GradleNotNullValue<Object>> secondResolvedVariables = second.getResolvedVariables();
    assertEquals(1, secondResolvedVariables.size());

    GradleNotNullValue<Object> first = secondResolvedVariables.get("first");
    assertNotNull(first);
    assertEquals(toSystemIndependentPath(myPropertiesFile.getPath()), toSystemIndependentPath(first.getFile().getPath()));
    assertNull(first.getPsiElement()); // There are no psi elements in the properties file.
    assertNull(first.getDslText());
    assertEquals("value_from_gradle_properties", first.value());
    assertEquals(0, first.getResolvedVariables().size());
  }

  public void testFlatDefVariablesAreResolved() throws IOException {
    String text = "def world = 'WORLD'\n" +
                  "def foo = 'bar'\n" +
                  "ext.first = \"Hello ${world}\"\n";


    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    // Make sure that we get the correct value for the property
    GradleNullableValue<String> value = extModel.getLiteralProperty("first", String.class);
    assertEquals("Hello WORLD", value);

    // But we also can't get the value from the def'd variables.
    // TODO: Work out if we need some way of accessing and editing these
    GradleNullableValue<String> defValue = extModel.getLiteralProperty("world", String.class);
    assertNull(defValue.value());
  }

  public void testNestedDefVariablesAreResolved() throws IOException {
    String text = "def world = 'world'\n" +
                  "def foo = 'bar'\n" +
                  "ext {\n" +
                  "    second = \"Welcome to $foo $world!\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    GradleNullableValue<String> value = extModel.getLiteralProperty("second", String.class);
    assertEquals("Welcome to bar world!", value.value());
  }


  public void testMultipleDefDeclarations() throws IOException {
    String text = "def olleh = 'hello', dlrow = 'world'\n" +
                  "ext.prop = \"hello $dlrow\"\n" +
                  "ext.prop2 = \"$olleh world\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    GradleNullableValue<String> propValue = extModel.getLiteralProperty("prop", String.class);
    assertEquals("hello world", propValue.value());
    GradleNullableValue<String> prop2Value = extModel.getLiteralProperty("prop2", String.class);
    assertEquals("hello world", prop2Value.value());
  }

  public void testDefUsedInDefResolved() throws IOException {
    String text = "def animal = 'penguin'\n" +
                  "def message = \"${animal}s are cool!\"\n" +
                  "ext.greeting = \"Hello, $message\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModelImpl extModel = (ExtModelImpl)buildModel.ext();

    GradleNullableValue<String> value = extModel.getLiteralProperty("greeting", String.class);
    assertEquals("Hello, penguins are cool!", value.value());
  }
}