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
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*;

/**
 * Tests for {@link ExtModel}.
 */
public class ExtModelTest extends GradleFileModelTestCase {
  @Test
  public void testParsingSimplePropertyPerLine() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "ext.srcDirName = 'src/java'";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("srcDirName"), STRING_TYPE, "src/java", STRING, REGULAR, 0);
  }

  @Test
  public void testParsingSimplePropertyInExtBlock() throws IOException {
    String text = "ext {\n" +
                  "   COMPILE_SDK_VERSION = 21\n" +
                  "   srcDirName = 'src/java'\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("srcDirName"), STRING_TYPE, "src/java", STRING, REGULAR, 0);
  }

  @Test
  public void testParsingListOfProperties() throws IOException {
    String text = "ext.libraries = [\n" +
                  "    guava: \"com.google.guava:guava:19.0-rc1\",\n" +
                  "    design :  \"com.android.support:design:22.2.1\"\n" +
                  "]";
    writeToBuildFile(text);


    ExtModel extModel = getGradleBuildModel().ext();
    GradlePropertyModel model = extModel.findProperty("libraries").toMap().get("guava");
    verifyPropertyModel(model, STRING_TYPE, "com.google.guava:guava:19.0-rc1", STRING, DERIVED, 0, "guava");
  }

  @Test
  public void testResolveExtProperty() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "android {\n" +
                  "  compileSdkVersion COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    GradlePropertyModel model = extModel.findProperty("COMPILE_SDK_VERSION");
    verifyPropertyModel(model, INTEGER_TYPE, 21, INTEGER, REGULAR, 0, "COMPILE_SDK_VERSION");

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    verifyPropertyModel(androidModel.compileSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1, "compileSdkVersion");
  }

  @Test
  public void testResolveQualifiedExtProperty() throws IOException {
    String text = "ext.constants = [\n" +
                  "  COMPILE_SDK_VERSION : 21\n" +
                  "]\n" +
                  "android {\n" +
                  "  compileSdkVersion constants.COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    GradlePropertyModel model = extModel.findProperty("constants").toMap().get("COMPILE_SDK_VERSION");
    verifyPropertyModel(model, INTEGER_TYPE, 21, INTEGER, DERIVED, 0, "COMPILE_SDK_VERSION");

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    verifyPropertyModel(androidModel.compileSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1, "compileSdkVersion");
  }

  @Test
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
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION"), STRING_TYPE, "SDK_VERSION", REFERENCE, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION").resolve(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", 21, defaultConfig.targetSdkVersion());
  }

  @Test
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
    verifyPropertyModel(extModel.findProperty("SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);


    GradleBuildModel subModuleBuildModel = getSubModuleGradleBuildModel();
    ExtModel subModuleExtModel = getSubModuleGradleBuildModel().ext();
    assertMissingProperty(subModuleExtModel.findProperty("SDK_VERSION"));

    AndroidModel androidModel = subModuleBuildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion()); // SDK_VERSION resolved from the main module.
  }

  @Test
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
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("ANDROID"), STRING_TYPE, "android", STRING, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("SDK_VERSION"), INTEGER_TYPE, 23, INTEGER, REGULAR, 0);


    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", "android-23", defaultConfig.targetSdkVersion());
  }

  @Test
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

  @Test
  public void testStringReferenceInListProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles 'proguard-android.txt', TEST_STRING\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  @Test
  public void testListReferenceInListProperty() throws IOException {
    String text = "ext.TEST_STRINGS = [\"test1\", \"test2\"]\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles TEST_STRINGS\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();

    verifyListProperty(extModel.findProperty("TEST_STRINGS"), ImmutableList.of("test1", "test2"));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("test1", "test2"), defaultConfig.proguardFiles());
  }

  @Test
  public void testResolveVariableInListProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    proguardFiles 'proguard-android.txt', \"$TEST_STRING\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  @Test
  public void testStringReferenceInMapProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:TEST_STRING\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  @Test
  public void testMapReferenceInMapProperty() throws IOException {
    String text = "ext.TEST_MAP = [test1:\"value1\", test2:\"value2\"]\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments TEST_MAP\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    GradlePropertyModel expressionMap = extModel.findProperty("TEST_MAP");
    assertNotNull(expressionMap);
    assertEquals("TEST_MAP", ImmutableMap.of("test1", "value1", "test2", "value2"), expressionMap);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertNotNull(defaultConfig);
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("test1", "value1", "test2", "value2"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  @Test
  public void testResolveVariableInMapProperty() throws IOException {
    String text = "ext.TEST_STRING = \"test\"\n" +
                  "android.defaultConfig {\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"$TEST_STRING\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  @Test
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

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_sub_module_build_file", STRING, REGULAR, 1);
  }

  @Test
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

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_sub_module_properties_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveVariableInMainModulePropertiesFile() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";
    String mainModuleBuildText = "ext.xyz = \"value_from_main_module_build_file\"";
    String subModuleBuildText = "ext.test = xyz";

    writeToSettingsFile(settingsText);
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModuleBuildFile(subModuleBuildText);

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_main_module_properties_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveVariableInMainModuleBuildFile() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModuleBuildText = "ext.xyz = \"value_from_main_module_build_file\"";
    String subModuleBuildText = "ext.test = xyz";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleBuildText);
    writeToSubModuleBuildFile(subModuleBuildText);

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_main_module_build_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveMultiLevelExtPropertyWithHistory() throws IOException {
    String text = "ext.FIRST = 123\n" +
                  "ext.SECOND = FIRST\n" +
                  "ext.THIRD = SECOND";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("THIRD").resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("THIRD"), STRING_TYPE, "SECOND", REFERENCE, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("SECOND").resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("SECOND"), STRING_TYPE, "FIRST", REFERENCE, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("FIRST").resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("FIRST"), INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
  }

  @Test
  public void testResolveMultiModuleExtPropertyWithHistory() throws IOException {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";
    String mainModuleText = "ext.FIRST = 123";
    String subModuleText = "ext.SECOND = FIRST";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    GradleBuildModel buildModel = getSubModuleGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    GradlePropertyModel second = extModel.findProperty("SECOND");
    verifyPropertyModel(second.resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 1);
    verifyPropertyModel(second, STRING_TYPE, "FIRST", REFERENCE, REGULAR, 1);
    GradlePropertyModel first = second.getDependencies().get(0);
    verifyPropertyModel(first.resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
    verifyPropertyModel(first, INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
  }

  @Test
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
    ExtModel extModel = buildModel.ext();

    GradlePropertyModel third = extModel.findProperty("third");
    verifyPropertyModel(third.resolve(), STRING_TYPE, "value_from_gradle_properties", STRING, REGULAR, 1);
    verifyPropertyModel(third, STRING_TYPE, "second", REFERENCE, REGULAR, 1);
    GradlePropertyModel second = third.getDependencies().get(0);
    verifyPropertyModel(second.resolve(), STRING_TYPE, "value_from_gradle_properties", STRING, REGULAR, 1);
    verifyPropertyModel(second, STRING_TYPE, "first", REFERENCE, REGULAR, 1);
    GradlePropertyModel first = second.getDependencies().get(0);
    verifyPropertyModel(first.resolve(), STRING_TYPE, "value_from_gradle_properties", STRING, PROPERTIES_FILE, 0);
    verifyPropertyModel(first, STRING_TYPE, "value_from_gradle_properties", STRING, PROPERTIES_FILE, 0);
  }

  @Test
  public void testFlatDefVariablesAreResolved() throws IOException {
    String text = "def world = 'WORLD'\n" +
                  "def foo = 'bar'\n" +
                  "ext.first = \"Hello ${world}\"\n";


    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("first").resolve(), STRING_TYPE, "Hello WORLD", STRING, REGULAR, 1);
  }

  @Test
  public void testNestedDefVariablesAreResolved() throws IOException {
    String text = "def world = 'world'\n" +
                  "def foo = 'bar'\n" +
                  "ext {\n" +
                  "    second = \"Welcome to $foo $world!\"\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("second").resolve(), STRING_TYPE, "Welcome to bar world!", STRING, REGULAR, 2);
  }

  @Test
  public void testMultipleDefDeclarations() throws IOException {
    String text = "def olleh = 'hello', dlrow = 'world'\n" +
                  "ext.prop = \"hello $dlrow\"\n" +
                  "ext.prop2 = \"$olleh world\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("prop").resolve(), STRING_TYPE, "hello world", STRING, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("prop2").resolve(), STRING_TYPE, "hello world", STRING, REGULAR, 1);
  }

  @Test
  public void testDefUsedInDefResolved() throws IOException {
    String text = "def animal = 'penguin'\n" +
                  "def message = \"${animal}s are cool!\"\n" +
                  "ext.greeting = \"Hello, $message\"";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("greeting").resolve(), STRING_TYPE, "Hello, penguins are cool!", STRING, REGULAR, 1);
  }

  @Test
  public void testDependencyExtUsage() throws IOException {
    String text = "buildscript {\n" +
                  "  dependencies {\n" +
                  "    ext.hello = 'boo'\n" +
                  "    classpath \"com.android.tools.build:gradle:$hello\"\n" +
                  "  }\n" +
                  "}\n" +
                  "ext.goodbye = hello\n" +
                  "ext.goodday = buildscript.dependencies.ext.hello";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel buildScriptDeps = buildModel.buildscript().dependencies();

    assertSize(1, buildScriptDeps.artifacts());
    ArtifactDependencyModel dependencyModel = buildScriptDeps.artifacts().get(0);
    verifyPropertyModel(dependencyModel.version(), STRING_TYPE, "boo", STRING, FAKE, 1);

    // Check outta Ext can't see the inner one.
    ExtModel extModel = buildModel.ext();
    verifyPropertyModel(extModel.findProperty("goodbye"), STRING_TYPE, "hello", REFERENCE, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("goodday").resolve(), STRING_TYPE, "boo", STRING, REGULAR, 1);
  }

  @Test
  public void testBuildScriptExtUsage() throws IOException {
    String text = "buildscript {\n" +
                  "  ext.hello = 'boo'\n" +
                  "  dependencies {\n" +
                  "    classpath \"com.android.tools.build:gradle:$hello\"\n" +
                  "  }\n" +
                  "}\n";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel buildScriptDeps = buildModel.buildscript().dependencies();

    assertSize(1, buildScriptDeps.artifacts());
    ArtifactDependencyModel dependencyModel = buildScriptDeps.artifacts().get(0);
    verifyPropertyModel(dependencyModel.version(), STRING_TYPE, "boo", STRING, FAKE, 1);
  }

  @Test
  public void testMultipleExtBlocks() throws IOException {
    String text =
      "apply plugin: 'com.android.application'\n" +
      "ext {\n" +
      "  var1 = \"1.5\"\n" +
      "}\n" +
      "android {\n" +
      "}\n" +
      "ext {\n" +
      "}\n" +

      "dependencies {\n" +
      "  compile fileTree(dir: 'libs', include: ['*.jar'])\n" +
      "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel ext = buildModel.ext();

    ext.findProperty("newProp").setValue(true);

    applyChangesAndReparse(buildModel);

    GradlePropertyModel propertyModel = ext.findProperty("newProp");
    verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);
    GradlePropertyModel oldModel = ext.findProperty("var1");
    verifyPropertyModel(oldModel, STRING_TYPE, "1.5", STRING, REGULAR,0);

    String expected =
      "apply plugin: 'com.android.application'\n" +
      "ext {\n" +
      "  var1 = \"1.5\"\n" +
      "  newProp = true\n" +
      "}\n" +
      "android {\n" +
      "}\n" +
      "ext {\n" +
      "}\n" +

      "dependencies {\n" +
      "  compile fileTree(dir: 'libs', include: ['*.jar'])\n" +
      "}";
    verifyFileContents(myBuildFile, expected);
  }

  @Test
  public void testExtFlatAndBlock() throws IOException {
    String text =
      "ext.hello = 10\n" +
      "ext {\n" +
      "  boo = '10'\n" +
      "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel ext = buildModel.ext();

    ext.findProperty("newProp").setValue(true);

    applyChangesAndReparse(buildModel);

    GradlePropertyModel helloModel = ext.findProperty("hello");
    verifyPropertyModel(helloModel, INTEGER_TYPE, 10, INTEGER, REGULAR, 0);
    GradlePropertyModel booModel = ext.findProperty("boo");
    verifyPropertyModel(booModel, STRING_TYPE, "10", STRING, REGULAR, 0);
    GradlePropertyModel newModel = ext.findProperty("newProp");

    verifyPropertyModel(newModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);

    String expected =
      "ext.hello = 10\n" +
      "ext {\n" +
      "  boo = '10'\n" +
      "  newProp = true\n" +
      "}";
    verifyFileContents(myBuildFile, expected);
  }
}