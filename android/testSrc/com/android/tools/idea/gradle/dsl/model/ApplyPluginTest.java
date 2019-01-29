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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiElement;
import org.junit.Test;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleBuildModelImpl} to test apply, add and remove plugins.
 */
public class ApplyPluginTest extends GradleFileModelTestCase {
  @Test
  public void testAppliedPluginsBlock() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAppliedPluginsBlockWithRepeatedPlugins() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "  plugin 'com.android.application'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testApplyPluginStatements() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testApplyPluginStatementsWithRepeatedPlugins() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'\n" +
                  "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlock() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins( ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "  plugin 'com.android.application'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatements() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'\n" +
                  "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlock() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "  plugin 'com.android.application'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatements() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'\n" +
                  "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAddAndResetPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.android.library");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
  }

  @Test
  public void testAddAndResetAlreadyExistingPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.applyPlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAddAndApplyPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.android.library");

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAddAndApplyAlreadyExistingPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyAppliedPluginsAndText(buildModel, text);

    buildModel.applyPlugin("com.android.application");
    verifyAppliedPluginsAndText(buildModel, text);

    applyChanges(buildModel);
    verifyAppliedPluginsAndText(buildModel, text);

    buildModel.reparse();
    verifyAppliedPluginsAndText(buildModel, text);
  }

  @Test
  public void testSetPluginName() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply {\n" +
                  "  plugin 'com.foo.bar'\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(2, buildModel.plugins());
    PluginModel pluginModel = buildModel.plugins().get(0);
    verifyPropertyModel(pluginModel.name(), STRING_TYPE, "com.android.application", STRING, DERIVED, 0, "plugin");
    PluginModel otherPlugin = buildModel.plugins().get(1);
    verifyPropertyModel(otherPlugin.name(), STRING_TYPE, "com.foo.bar", STRING, REGULAR, 0, "plugin");

    pluginModel.name().setValue("com.google.application");
    otherPlugin.name().setValue("bar.com.foo");
    applyChangesAndReparse(buildModel);

    assertSize(2, buildModel.plugins());
    pluginModel = buildModel.plugins().get(0);
     verifyPropertyModel(pluginModel.name(), STRING_TYPE, "com.google.application", STRING, DERIVED, 0, "plugin");
    otherPlugin = buildModel.plugins().get(1);
    verifyPropertyModel(otherPlugin.name(), STRING_TYPE, "bar.com.foo", STRING, REGULAR, 0, "plugin");
  }

  @Test
  public void testDeletePluginName() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply {\n" +
                  "  plugin 'com.foo.bar'\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(2, buildModel.plugins());
    PluginModel pluginModel = buildModel.plugins().get(0);
    PluginModel otherPlugin = buildModel.plugins().get(1);

    pluginModel.name().delete();
    otherPlugin.name().delete();
    applyChangesAndReparse(buildModel);

    assertSize(0, buildModel.plugins());
  }

  @Test
  public void testInsertPluginOrder() throws Exception {
    String text = "";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.applyPlugin("kotlin-android");
    buildModel.applyPlugin("kotlin-plugin-extensions");
    buildModel.applyPlugin("some-other-plugin");

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, DERIVED, 0);

    applyChangesAndReparse(buildModel);

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, DERIVED, 0);

    String expected = "apply plugin: 'kotlin-android'\n" +
                      "apply plugin: 'kotlin-plugin-extensions'\n" +
                      "apply plugin: 'some-other-plugin'";
    verifyFileContents(myBuildFile, expected);
  }

  @Test
  public void testApplyPluginAtStart() throws Exception {
    String text = "allprojects {\n" +
                  "  repositories {\n" +
                  "    jcenter()\n" +
                  "  }\n" +
                  "}\n" +
                  "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.applyPlugin("kotlin-android");
    buildModel.applyPlugin("kotlin-plugin-extensions");
    buildModel.applyPlugin("some-other-plugin");

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, DERIVED, 0);

    applyChangesAndReparse(buildModel);

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, DERIVED, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, DERIVED, 0);

    String expected = "apply plugin: 'kotlin-android'\n" +
                      "apply plugin: 'kotlin-plugin-extensions'\n" +
                      "apply plugin: 'some-other-plugin'\n" +
                      "allprojects {\n" +
                      "  repositories {\n" +
                      "    jcenter()\n" +
                      "  }\n" +
                      "}\n" +
                      "repositories {\n" +
                      "  google()\n" +
                      "}";
    verifyFileContents(myBuildFile, expected);
  }

  @Test
  public void testAppliedPluginCompatibility() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply {\n" +
                  "  plugin 'com.foo.bar'\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<GradleNotNullValue<String>> plugins = buildModel.appliedPlugins();
    assertSize(2, plugins);

    GradleNotNullValue<String> first = plugins.get(0);
    assertEquals("com.android.application", first.value());
    assertEquals("plugin", first.getPropertyName());
    assertEquals("plugin: 'com.android.application'", first.getDslText());
    assertEquals(VfsUtil.findFileByIoFile(myBuildFile, false), first.getFile());
  }

  private static void verifyAppliedPluginsAndText(GradleBuildModel buildModel, String buildText) {
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
    assertThat(buildModel).isInstanceOf(GradleBuildModelImpl.class);
    GradleBuildModelImpl buildModelImpl = (GradleBuildModelImpl)buildModel;

    PsiElement buildFilePsiElement = buildModelImpl.getPsiElement();
    assertNotNull(buildFilePsiElement);
    assertEquals("buildText", buildText, buildFilePsiElement.getText());
  }
}
