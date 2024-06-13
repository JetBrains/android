/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.List;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PluginsBlockTest extends GradleFileModelTestCase {

  @Before
  public void before() throws Exception {
    Registry.get("android.gradle.declarative.plugin.studio.support").setValue(true);
    super.before();
  }

  @After
  public void onAfter() {
    Registry.get("android.gradle.declarative.plugin.studio.support").resetToDefault();
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
  public void testAddPluginAfterBuildscript() throws Exception {
    writeToBuildFile(TestFile.ADD_PLUGIN_AFTER_BUILDSCRIPT);
    // TODO - add support for declarative
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
    // TODO - add support for declarative
    GradleBuildModel buildModel = getGradleBuildModel();
    assertSize(0, buildModel.plugins());

    buildModel.applyPlugin("com.android.application", "4.2.0", false);
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PLUGIN_DSL_AFTER_BUILDSCRIPT_EXPECTED);
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
  public void testApplyPluginsFromPluginsBlock() throws Exception {
    writeToBuildFile(TestFile.APPLY_PLUGINS_FROM_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("maven-publish", "jacoco"), buildModel.plugins());
    verifyPlugins(ImmutableList.of("maven-publish", "jacoco"), buildModel.appliedPlugins());
  }

  enum TestFile implements TestFileName {
    ADD_PLUGIN_TO_PLUGINS_BLOCK("addPluginToPluginsBlock"),
    ADD_PLUGIN_TO_PLUGINS_BLOCK_EXPECTED("addPluginToPluginsBlockExpected"),
    ADD_PLUGIN_DSL_TO_PLUGINS_BLOCK_EXPECTED("addPluginDslToPluginsBlockExpected"),
    ADD_PLUGIN_AFTER_BUILDSCRIPT("addPluginAfterBuildscript"),
    ADD_PLUGIN_AFTER_BUILDSCRIPT_EXPECTED("addPluginAfterBuildscriptExpected"),
    ADD_PLUGIN_DSL_AFTER_BUILDSCRIPT_EXPECTED("addPluginDslAfterBuildscriptExpected"),
    ADD_EXISTING_PLUGIN_TO_PLUGINS_AND_APPLY_BLOCKS("addExistingPluginToPluginsAndApplyBlocks"),
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
      return TestFileName.super.toFile(basePath + "/pluginsBlock/" + path, extension);
    }
  }
}
