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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.BuildEnvironment;

import java.io.File;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.testing.TestProjectPaths.PLUGIN_IN_APP;

/**
 * Tests for {@link AndroidPluginInfo}.
 */
public class AndroidPluginInfoTest extends AndroidGradleTestCase {
  public void testFindWithStablePlugin() throws Exception {
    loadSimpleApplication();
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.find(getProject());
    assertNotNull(androidPluginInfo);
    assertNotNull(androidPluginInfo.getModule());
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertNull(androidPluginInfo.getPluginBuildFile());

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(BuildEnvironment.getInstance().getGradlePluginVersion(), pluginVersion.toString());
  }

  public void testFindWithStablePluginReadingBuildFilesOnly() throws Exception {
    loadSimpleApplication();
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(getProject());
    assertNotNull(androidPluginInfo);
    assertNotNull(androidPluginInfo.getModule());
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertNotNull(androidPluginInfo.getPluginBuildFile());
    assertEquals(new File(getProjectFolderPath(), FN_BUILD_GRADLE),
                 new File(androidPluginInfo.getPluginBuildFile().getPath()));

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(BuildEnvironment.getInstance().getGradlePluginVersion(), pluginVersion.toString());
  }

  public void testFindWithStablePluginInAppReadingBuildFilesOnly() throws Exception {
    loadProject(PLUGIN_IN_APP);
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(getProject());
    assertNotNull(androidPluginInfo);
    assertNotNull(androidPluginInfo.getModule());
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertNotNull(androidPluginInfo.getPluginBuildFile());
    assertEquals(new File(new File(getProjectFolderPath(), "app"), FN_BUILD_GRADLE),
                 new File(androidPluginInfo.getPluginBuildFile().getPath()));

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(BuildEnvironment.getInstance().getGradlePluginVersion(), pluginVersion.toString());
  }

  public void testFindWithOriginalArtifactIdAndGroupId() {
    boolean isAndroidPlugin = AndroidPluginInfo.isAndroidPlugin("gradle", "com.android.tools.build");
    assertTrue(isAndroidPlugin);
  }

  public void testFindWithWRONGArtifactIdAndGroupId() {
    boolean isAndroidPlugin = AndroidPluginInfo.isAndroidPlugin("HELLO", "WORLD");
    assertFalse(isAndroidPlugin);
  }
}