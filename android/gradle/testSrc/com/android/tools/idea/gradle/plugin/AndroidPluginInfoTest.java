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
package com.android.tools.idea.gradle.plugin;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.BuildEnvironment;
import com.android.tools.idea.testing.TestModuleUtil;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

import static com.android.tools.idea.testing.TestProjectPaths.PLUGIN_IN_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidPluginInfo}.
 */
public class AndroidPluginInfoTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Test
  public void testFindWithStablePlugin() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.find(projectRule.getProject());
    assertThat(androidPluginInfo).isNotNull();
    assertThat(androidPluginInfo.getModule()).isNotNull();
    assertThat(androidPluginInfo.getModule()).isEqualTo(TestModuleUtil.findAppModule(projectRule.getProject()));
    assertThat(androidPluginInfo.getPluginBuildFile()).isNull();

    AgpVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertThat(pluginVersion).isNotNull();
    assertThat(pluginVersion.toString()).isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
  }

  @Test
  public void testFindWithStablePluginReadingBuildFilesOnly() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromBuildFiles(projectRule.getProject());
    assertThat(androidPluginInfo).isNotNull();
    assertThat(androidPluginInfo.getModule()).isNotNull();
    assertThat(androidPluginInfo.getModule()).isEqualTo(TestModuleUtil.findAppModule(projectRule.getProject()));
    assertThat(androidPluginInfo.getPluginBuildFile()).isNotNull();
    assertThat(new File(androidPluginInfo.getPluginBuildFile().getPath()))
      .isEqualTo(new File(projectRule.getProject().getBasePath(), "build.gradle"));
    AgpVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertThat(pluginVersion).isNotNull();
    assertThat(pluginVersion.toString()).isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
  }

  @Test
  public void testFindWithStablePluginInAppReadingBuildFilesOnly() {
    projectRule.loadProject(PLUGIN_IN_APP);
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromBuildFiles(projectRule.getProject());
    assertThat(androidPluginInfo).isNotNull();
    assertThat(androidPluginInfo.getModule()).isNotNull();
    assertThat(androidPluginInfo.getModule()).isEqualTo(TestModuleUtil.findAppModule(projectRule.getProject()));
    assertThat(androidPluginInfo.getPluginBuildFile()).isNotNull();
    assertThat(new File(androidPluginInfo.getPluginBuildFile().getPath()))
      .isEqualTo(new File(projectRule.getProject().getBasePath(), "app/build.gradle"));
    AgpVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertThat(pluginVersion).isNotNull();
    assertThat(pluginVersion.toString()).isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
  }

  @Test
  public void testFindWithStablePluginInAppFromModelsOnly() {
    projectRule.loadProject(SIMPLE_APPLICATION);

    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(projectRule.getProject());
    assertThat(androidPluginInfo).isNotNull();
    assertThat(androidPluginInfo.getModule()).isNotNull();
    assertThat(androidPluginInfo.getModule()).isEqualTo(TestModuleUtil.findAppModule(projectRule.getProject()));
    assertThat(androidPluginInfo.getPluginBuildFile()).isNull();
    AgpVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertThat(pluginVersion).isNotNull();
    assertThat(pluginVersion.toString()).isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
  }

  @Test
  public void testFindWithOriginalArtifactIdAndGroupId() {
    boolean isAndroidPlugin = AndroidPluginInfo.isAndroidPlugin("gradle", "com.android.tools.build");
    assertThat(isAndroidPlugin).isTrue();
  }

  @Test
  public void testFindWithWRONGArtifactIdAndGroupId() {
    boolean isAndroidPlugin = AndroidPluginInfo.isAndroidPlugin("HELLO", "WORLD");
    assertThat(isAndroidPlugin).isFalse();
  }
}