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
import com.android.tools.idea.templates.AndroidGradleTestCase;

import static com.android.SdkConstants.GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.COMPONENT;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.ORIGINAL;

/**
 * Tests for {@link AndroidPluginInfo}.
 */
public class AndroidPluginInfoTest extends AndroidGradleTestCase {
  public void testFindWithExperimentalPlugin() throws Exception {
    loadProject("projects/experimentalPlugin");
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.find(getProject());
    assertNotNull(androidPluginInfo);
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertEquals(COMPONENT, androidPluginInfo.getPluginGeneration());

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION, pluginVersion.toString());
  }

  public void testFindWithExperimentalPluginReadingBuildFilesOnly() throws Exception {
    loadProject("projects/experimentalPlugin");
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(getProject());
    assertNotNull(androidPluginInfo);
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertEquals(COMPONENT, androidPluginInfo.getPluginGeneration());

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION, pluginVersion.toString());
  }

  public void testFindWithStablePlugin() throws Exception {
    loadSimpleApplication();
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.find(getProject());
    assertNotNull(androidPluginInfo);
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertEquals(ORIGINAL, androidPluginInfo.getPluginGeneration());

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(GRADLE_PLUGIN_RECOMMENDED_VERSION, pluginVersion.toString());
  }

  public void testFindWithStablePluginReadingBuildFilesOnly() throws Exception {
    loadSimpleApplication();
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(getProject());
    assertNotNull(androidPluginInfo);
    assertEquals("app", androidPluginInfo.getModule().getName());
    assertEquals(ORIGINAL, androidPluginInfo.getPluginGeneration());

    GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);
    assertEquals(GRADLE_PLUGIN_RECOMMENDED_VERSION, pluginVersion.toString());
  }
}