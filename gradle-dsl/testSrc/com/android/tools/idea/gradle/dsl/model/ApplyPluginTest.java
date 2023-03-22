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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link GradleBuildModelImpl} to test apply, add and remove plugins.
 */
public class ApplyPluginTest extends GradleFileModelTestCase {
  @Test
  public void testAppliedPluginsBlock() throws Exception {
    writeToBuildFile(TestFile.APPLIED_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAppliedPluginsBlockWithRepeatedPlugins() throws Exception {
    writeToBuildFile(TestFile.APPLIED_PLUGINS_BLOCK_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testApplyPluginStatements() throws Exception {
    writeToBuildFile(TestFile.APPLY_PLUGIN_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testApplyPluginStatementsWithRepeatedPlugins() throws Exception {
    writeToBuildFile(TestFile.APPLY_PLUGIN_STATEMENTS_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlock() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlock() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndResetPlugin() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_RESET_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.library");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndResetAlreadyExistingPlugin() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_RESET_ALREADY_EXISTING_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndApplyPlugin() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.library");

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_PLUGIN_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndApplyAlreadyExistingPlugin() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_ALREADY_EXISTING_PLUGIN);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_ALREADY_EXISTING_PLUGIN);

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testSetPluginName() throws Exception {
    writeToBuildFile(TestFile.SET_PLUGIN_NAME);
    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(2, buildModel.plugins());
    PluginModel pluginModel = buildModel.plugins().get(0);
    verifyPropertyModel(pluginModel.name(), STRING_TYPE, "com.android.application", STRING, DERIVED, 0, "plugin");
    PluginModel otherPlugin = buildModel.plugins().get(1);
    verifyPropertyModel(otherPlugin.name(), STRING_TYPE, "com.foo.bar", STRING, REGULAR, 0, "plugin");

    pluginModel.name().setValue("com.google.application");
    otherPlugin.name().setValue("bar.com.foo");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_PLUGIN_NAME_EXPECTED);

    assertSize(2, buildModel.plugins());
    pluginModel = buildModel.plugins().get(0);
    verifyPropertyModel(pluginModel.name(), STRING_TYPE, "com.google.application", STRING, DERIVED, 0, "plugin");
    otherPlugin = buildModel.plugins().get(1);
    verifyPropertyModel(otherPlugin.name(), STRING_TYPE, "bar.com.foo", STRING, REGULAR, 0, "plugin");
  }

  @Test
  public void testDeletePluginName() throws Exception {
    writeToBuildFile(TestFile.DELETE_PLUGIN_NAME);
    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(2, buildModel.plugins());
    assertSize(2, buildModel.appliedPlugins());
    PluginModel pluginModel = buildModel.plugins().get(0);
    PluginModel otherPlugin = buildModel.plugins().get(1);

    pluginModel.name().delete();
    otherPlugin.name().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    assertSize(0, buildModel.plugins());
    assertSize(0, buildModel.appliedPlugins());
  }

  @Test
  public void testInsertPluginOrder() throws Exception {
    writeToBuildFile(TestFile.INSERT_PLUGIN_ORDER);
    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.applyPlugin("kotlin-android");
    buildModel.applyPlugin("kotlin-plugin-extensions");
    buildModel.applyPlugin("some-other-plugin");

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.INSERT_PLUGIN_ORDER_EXPECTED);

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);
  }

  @Test
  public void testApplyPluginAtStart() throws Exception {
    writeToBuildFile(TestFile.APPLY_PLUGIN_AT_START);
    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.applyPlugin("kotlin-android");
    buildModel.applyPlugin("kotlin-plugin-extensions");
    buildModel.applyPlugin("some-other-plugin");

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.APPLY_PLUGIN_AT_START_EXPECTED);

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);
  }

  @Test
  public void testAppliedKotlinPlugin() throws Exception {
    writeToBuildFile(TestFile.APPLIED_KOTLIN_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("org.jetbrains.kotlin.android", "org.jetbrains.kotlin.plugin-extensions"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("org.jetbrains.kotlin.android", "org.jetbrains.kotlin.plugin-extensions"), buildModel.appliedPlugins());
  }

  @Test
  public void testApplyPluginsFromPluginsBlock() throws Exception {
    writeToBuildFile(TestFile.APPLY_PLUGINS_FROM_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("maven-publish", "jacoco"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("maven-publish", "jacoco"), buildModel.appliedPlugins());
  }

  @Test
  public void testPluginsBlockWithRepeatedPlugins() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testPluginsBlockWithVersion() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "2.3")), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "2.3")), buildModel.appliedPlugins());
  }

  @Test
  public void testPluginsBlockWithApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of(), buildModel.appliedPlugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("apply", false)), buildModel.plugins());
  }

  @Test
  public void testPluginsBlockWithVersionAndApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of(), buildModel.appliedPlugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "2.3", "apply", false)), buildModel.plugins());
  }

  @Test
  public void testPluginsBlockWithVersionSetApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    assertEquals(STRING, pluginModel.version().getValueType());
    assertEquals(NONE, pluginModel.apply().getValueType());
    pluginModel.apply().setValue(true);
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_VERSION_SET_APPLY_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "2.3", "apply", true)), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "2.3", "apply", true)), buildModel.appliedPlugins());
  }

  @Test
  public void testPluginsBlockWithVersionSetVersion() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    pluginModel.version().setValue("3.4");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_VERSION_SET_VERSION_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "3.4")), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "3.4")), buildModel.appliedPlugins());
  }

  @Test
  public void testPluginsBlockWithApplySetApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    pluginModel.apply().setValue(true);
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_APPLY_SET_APPLY_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("apply", true)), buildModel.plugins());
  }

  @Test
  public void testPluginsBlockWithApplySetVersion() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    assertEquals(NONE, pluginModel.version().getValueType());
    assertEquals(BOOLEAN, pluginModel.apply().getValueType());
    pluginModel.version().setValue("3.4");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_APPLY_SET_VERSION_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "3.4", "apply", false)), buildModel.plugins());
  }

  @Test
  public void testPluginsBlockWithVersionAndApplySetVersion() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    pluginModel.version().setValue("3.4");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_VERSION_AND_APPLY_SET_VERSION_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "3.4", "apply", false)), buildModel.plugins());
  }

  @Test
  public void testPluginsBlockWithVersionAndApplySetApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    pluginModel.apply().setValue(true);
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_VERSION_AND_APPLY_SET_APPLY_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "2.3", "apply", true)), buildModel.plugins());
  }

  @Test
  public void testModifiedInPluginsBlockWithMultiplePlugins() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_MULTIPLE_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<PluginModel> plugins = buildModel.plugins();
    verifyPlugins(ImmutableList.of("org.jetbrains.kotlin.android", "com.android.application"), plugins);
    assertFalse(plugins.get(0).name().isModified());
    assertFalse(plugins.get(0).version().isModified());
    assertFalse(plugins.get(1).name().isModified());
    assertFalse(plugins.get(1).version().isModified());
    plugins.get(0).version().setValue("1.4.10");
    assertFalse(plugins.get(0).name().isModified());
    assertTrue(plugins.get(0).version().isModified());
    assertFalse(plugins.get(1).name().isModified());
    assertFalse(plugins.get(1).version().isModified());
    plugins.get(1).name().setValue("com.android.library");
    assertFalse(plugins.get(0).name().isModified());
    assertTrue(plugins.get(0).version().isModified());
    assertTrue(plugins.get(1).name().isModified());
    assertFalse(plugins.get(1).version().isModified());
  }

  @Test
  public void testPluginsBlockNoDslSetVersion() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_NO_DSL);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    assertEquals(NONE, pluginModel.version().getValueType());
    assertEquals(NONE, pluginModel.apply().getValueType());
    pluginModel.version().setValue("3.4");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_NO_DSL_SET_VERSION_EXPECTED);
  }

  @Test
  public void testPluginsBlockNoDslSetApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_NO_DSL);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.appliedPlugins());
    assertEquals(NONE, pluginModel.version().getValueType());
    assertEquals(NONE, pluginModel.apply().getValueType());
    pluginModel.apply().setValue(false);
    verifyPlugins(ImmutableList.of(), buildModel.appliedPlugins());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_NO_DSL_SET_APPLY_EXPECTED);
  }

  @Test
  public void testPluginsBlockNoDslSetVersionAndApply() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_NO_DSL);
    GradleBuildModel buildModel = getGradleBuildModel();
    PluginModel pluginModel = buildModel.plugins().get(0);
    assertEquals(NONE, pluginModel.version().getValueType());
    assertEquals(NONE, pluginModel.apply().getValueType());
    pluginModel.version().setValue("3.4");
    // TODO(b/175192157): this is a bit of a hack: we need to regenerate the plugin model because the previous transform invalidates the
    //  underlying Dsl.  (I think this is a semi-general issue with PropertyTransforms)
    pluginModel = buildModel.plugins().get(0);
    pluginModel.apply().setValue(false);
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_NO_DSL_SET_VERSION_AND_APPLY_EXPECTED);
  }

  @Test
  public void testPluginsDslParseKotlinFunction() throws Exception {
    isIrrelevantForGroovy("kotlin function not supported in plugins { } block in Groovy");
    writeToBuildFile(TestFile.PLUGINS_DSL_PARSE_KOTLIN_FUNCTION);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<PluginModel> pluginModels = buildModel.plugins();
    verifyPlugins(ImmutableList.of("com.android.application", "org.jetbrains.kotlin.android"), pluginModels);
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of(),
                                  "org.jetbrains.kotlin.android", ImmutableMap.of("version", "3.2")),
                  pluginModels);
  }

  @Test
  public void testPluginsFromApplyAndPluginsBlock() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testApplyRepeatedPluginsFromApplyAndPluginsBlocks() throws Exception {
    writeToBuildFile(TestFile.APPLY_REPEATED_PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<PluginModel> plugins = buildModel.plugins();
    assertSize(3, plugins);
    verifyPlugins(ImmutableList.of("maven-publish", "com.android.application", "com.android.library"), plugins);
    verifyPlugins(ImmutableList.of("maven-publish", "com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddPluginToPluginsBlock() throws Exception {
    writeToBuildFile(TestFile.ADD_PLUGIN_TO_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("kotlin-android");
    List<PluginModel> plugins = buildModel.plugins();
    verifyPlugins(ImmutableList.of("com.android.application", "kotlin-android"), plugins);
    verifyPlugins(ImmutableList.of("com.android.application", "kotlin-android"), buildModel.appliedPlugins());
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PLUGIN_TO_PLUGINS_BLOCK_EXPECTED);
  }

  @Test
  public void testAddExistingPluginToPluginsAndApplyBlock() throws Exception {
    writeToBuildFile(TestFile.ADD_EXISTING_PLUGIN_TO_PLUGINS_AND_APPLY_BLOCKS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("kotlin-android", "com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("kotlin-android");
    List<PluginModel> plugins = buildModel.plugins();
    assertSize(2, plugins);
    verifyPlugins(ImmutableList.of("com.android.application", "kotlin-android"), plugins);
    verifyPlugins(ImmutableList.of("com.android.application", "kotlin-android"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddPluginDslWithPluginsBlock() throws Exception {
    writeToBuildFile(TestFile.ADD_PLUGIN_TO_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.google.firebase.crashlytics", "17.3.0");
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.appliedPlugins());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PLUGIN_DSL_TO_PLUGINS_BLOCK_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddPluginDslWithApplyBlock() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.google.firebase.crashlytics", "17.3.0", true);
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.appliedPlugins());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_PLUGIN_DSL_EXPECTED);
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("com.android.application", "com.google.firebase.crashlytics"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddPluginAfterBuildscript() throws Exception {
    writeToBuildFile(TestFile.ADD_PLUGIN_AFTER_BUILDSCRIPT);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertSize(0, buildModel.plugins());

    buildModel.applyPlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PLUGIN_AFTER_BUILDSCRIPT_EXPECTED);
  }

  @Test
  public void testAddPluginDslAfterBuildscript() throws Exception {
    writeToBuildFile(TestFile.ADD_PLUGIN_AFTER_BUILDSCRIPT);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertSize(0, buildModel.plugins());

    buildModel.applyPlugin("com.android.application", "4.2.0", false);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PLUGIN_DSL_AFTER_BUILDSCRIPT_EXPECTED);
  }

  @Test
  public void testOnlyParsePluginsWithCorrectSyntax() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_UNSUPPORTED_SYNTAX);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testParsePluginBlockWithAnnotation() throws Exception {
    isIrrelevantForGroovy("Annotations are not supported in Groovy build files");
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_ANNOTATIONS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.android.library");
    applyChangesAndReparse(buildModel);

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    verifyFileContents(myBuildFile, TestFile.PLUGINS_BLOCK_WITH_ANNOTATIONS_EXPECTED);
  }

  @Test
  public void testPluginRemove() throws Exception {
    writeToBuildFile(TestFile.PLUGIN_REMOVE);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "io.fabric"), buildModel.plugins());

    for (PluginModel plugin : buildModel.plugins()) {
      if (plugin.name().toString().equals("io.fabric")) {
        plugin.remove();
      }
    }

    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.PLUGIN_REMOVE_EXPECTED);
  }

  @Test
  public void testRemovePluginFromPluginDsl() throws Exception {
    writeToBuildFile(TestFile.PLUGINS_BLOCK_WITH_VERSION_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    buildModel.plugins().get(0).remove();
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");
  }

  @Test
  public void testPluginIdMethodCall() throws Exception {
    isIrrelevantForKotlinScript("All plugin Dsl calls are method calls");
    writeToBuildFile(TestFile.PLUGINS_BLOCK_ID_METHOD_CALL);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
  }

  @Test
  public void testPluginDslMethodCall() throws Exception {
    isIrrelevantForKotlinScript("All plugin Dsl calls are method calls");
    writeToBuildFile(TestFile.PLUGINS_BLOCK_DSL_METHOD_CALL);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableMap.of("com.android.application", ImmutableMap.of("version", "7.1.0")), buildModel.plugins());
  }

  @Test
  public void testGetPsiElement() throws Exception {
    writeToBuildFile(TestFile.APPLIED_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    assertNotNull(buildModel.plugins().get(0).getPsiElement());
    assertEquals("com.android.application", buildModel.plugins().get(0).getPsiElement().getText().replaceAll("['\"]", ""));
    assertNotNull(buildModel.plugins().get(1).getPsiElement());
    assertEquals("com.android.library", buildModel.plugins().get(1).getPsiElement().getText().replaceAll("['\"]", ""));
  }

  enum TestFile implements TestFileName {
    ADD_PLUGIN_TO_PLUGINS_BLOCK("addPluginToPluginsBlock"),
    ADD_PLUGIN_TO_PLUGINS_BLOCK_EXPECTED("addPluginToPluginsBlockExpected"),
    ADD_PLUGIN_DSL_TO_PLUGINS_BLOCK_EXPECTED("addPluginDslToPluginsBlockExpected"),
    ADD_PLUGIN_AFTER_BUILDSCRIPT("addPluginAfterBuildscript"),
    ADD_PLUGIN_AFTER_BUILDSCRIPT_EXPECTED("addPluginAfterBuildscriptExpected"),
    ADD_PLUGIN_DSL_AFTER_BUILDSCRIPT_EXPECTED("addPluginDslAfterBuildscriptExpected"),
    APPLIED_KOTLIN_PLUGIN("appliedKotlinPlugin"),
    APPLIED_PLUGINS_BLOCK("appliedPluginsBlock"),
    APPLIED_PLUGINS_BLOCK_WITH_REPEATED_PLUGINS("appliedPluginsBlockWithRepeatedPlugins"),
    APPLY_PLUGIN_STATEMENTS("applyPluginStatements"),
    APPLY_PLUGIN_STATEMENTS_WITH_REPEATED_PLUGINS("applyPluginStatementsWithRepeatedPlugins"),
    REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK("removeAndResetPluginFromApplyBlock"),
    REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN("removeAndResetPluginFromApplyBlockWithDuplicatedPlugin"),
    REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS("removeAndResetPluginFromApplyStatements"),
    REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS("removeAndResetPluginFromApplyStatementsWithRepeatedPlugins"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK("removeAndApplyPluginFromApplyBlock"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_EXPECTED("removeAndApplyPluginFromApplyBlockExpected"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN("removeAndApplyPluginFromApplyBlockWithDuplicatedPlugin"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN_EXPECTED("removeAndApplyPluginFromApplyBlockWithDuplicatedPluginExpected"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS("removeAndApplyPluginFromApplyStatements"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_EXPECTED("removeAndApplyPluginFromApplyStatementsExpected"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS("removeAndApplyPluginFromApplyStatementsWithRepeatedPlugins"),
    REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS_EXPECTED("removeAndApplyPluginFromApplyStatementsWithRepeatedPluginsExpected"),
    ADD_AND_RESET_PLUGIN("addAndResetPlugin"),
    ADD_AND_RESET_ALREADY_EXISTING_PLUGIN("addAndResetAlreadyExistingPlugin"),
    ADD_AND_APPLY_PLUGIN("addAndApplyPlugin"),
    ADD_AND_APPLY_PLUGIN_EXPECTED("addAndApplyPluginExpected"),
    ADD_AND_APPLY_PLUGIN_DSL_EXPECTED("addAndApplyPluginDslExpected"),
    ADD_AND_APPLY_ALREADY_EXISTING_PLUGIN("addAndApplyAlreadyExistingPlugin"),
    ADD_EXISTING_PLUGIN_TO_PLUGINS_AND_APPLY_BLOCKS("addExistingPluginToPluginsAndApplyBlocks"),
    SET_PLUGIN_NAME("setPluginName"),
    SET_PLUGIN_NAME_EXPECTED("setPluginNameExpected"),
    DELETE_PLUGIN_NAME("deletePluginName"),
    INSERT_PLUGIN_ORDER("insertPluginOrder"),
    INSERT_PLUGIN_ORDER_EXPECTED("insertPluginOrderExpected"),
    APPLY_PLUGIN_AT_START("applyPluginAtStart"),
    APPLY_PLUGIN_AT_START_EXPECTED("applyPluginAtStartExpected"),
    APPLY_REPEATED_PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK("applyRepeatedPluginsFromApplyAndPluginsBlocks"),
    APPLY_PLUGINS_FROM_PLUGINS_BLOCK("applyPluginsFromPluginsBlock"),
    PLUGINS_BLOCK_NO_DSL("pluginsBlockNoDsl"),
    PLUGINS_BLOCK_NO_DSL_SET_APPLY_EXPECTED("pluginsBlockNoDslSetApplyExpected"),
    PLUGINS_BLOCK_NO_DSL_SET_VERSION_EXPECTED("pluginsBlockNoDslSetVersionExpected"),
    PLUGINS_BLOCK_NO_DSL_SET_VERSION_AND_APPLY_EXPECTED("pluginsBlockNoDslSetVersionAndApplyExpected"),
    PLUGINS_BLOCK_WITH_REPEATED_PLUGINS("pluginsBlockWithRepeatedPlugins"),
    PLUGINS_BLOCK_WITH_VERSION("pluginsBlockWithVersion"),
    PLUGINS_BLOCK_WITH_VERSION_SET_APPLY_EXPECTED("pluginsBlockWithVersionSetApplyExpected"),
    PLUGINS_BLOCK_WITH_VERSION_SET_VERSION_EXPECTED("pluginsBlockWithVersionSetVersionExpected"),
    PLUGINS_BLOCK_WITH_APPLY("pluginsBlockWithApply"),
    PLUGINS_BLOCK_WITH_APPLY_SET_APPLY_EXPECTED("pluginsBlockWithApplySetApplyExpected"),
    PLUGINS_BLOCK_WITH_APPLY_SET_VERSION_EXPECTED("pluginsBlockWithApplySetVersionExpected"),
    PLUGINS_BLOCK_WITH_VERSION_AND_APPLY("pluginsBlockWithVersionAndApply"),
    PLUGINS_BLOCK_WITH_VERSION_AND_APPLY_SET_VERSION_EXPECTED("pluginsBlockWithVersionAndApplySetVersionExpected"),
    PLUGINS_BLOCK_WITH_VERSION_AND_APPLY_SET_APPLY_EXPECTED("pluginsBlockWithVersionAndApplySetApplyExpected"),
    PLUGINS_BLOCK_WITH_MULTIPLE_PLUGINS("pluginsBlockWithMultiplePlugins"),
    PLUGINS_DSL_PARSE_KOTLIN_FUNCTION("pluginsDslParseKotlinFunction"),
    PLUGINS_BLOCK_ID_METHOD_CALL("pluginsBlockIdMethodCall"),
    PLUGINS_BLOCK_DSL_METHOD_CALL("pluginsBlockDslMethodCall"),
    PLUGINS_UNSUPPORTED_SYNTAX("pluginsWithUnsupportedSyntax"),
    PLUGINS_BLOCK_WITH_ANNOTATIONS("pluginsBlockWithAnnotations"),
    PLUGINS_BLOCK_WITH_ANNOTATIONS_EXPECTED("pluginsBlockWithAnnotationsExpected"),
    PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK("pluginsFromApplyAndPluginsBlock"),
    PLUGIN_REMOVE("pluginRemove"),
    PLUGIN_REMOVE_EXPECTED("pluginRemoveExpected")
    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/applyPlugin/" + path, extension);
    }
  }
}
