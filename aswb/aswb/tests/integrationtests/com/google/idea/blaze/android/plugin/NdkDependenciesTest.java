/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.plugin;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.settings.BlazeAndroidUserSettings;
import com.google.idea.blaze.android.sync.BlazeNdkDependencySyncPlugin;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.testing.DisablePluginsTestRule;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test that ASwB successfully loads when the NDK plugins aren't enabled. Must be run as a
 * stand-alone test, because it requires its own {@link Application}, and only one such object can
 * be constructed per test suite.
 */
@RunWith(JUnit4.class)
public class NdkDependenciesTest extends BlazeIntegrationTestCase {

  @ClassRule
  public static TestRule setDisabledPlugins =
      new DisablePluginsTestRule(
          BlazeNdkDependencySyncPlugin.getPluginsRequiredForNdkSupport().asList());

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  @Ignore("b/337933435")
  @Test
  public void testNdkPluginsInstalled() {
    assertThat(PluginUtils.isPluginInstalled("com.android.tools.ndk")).isTrue();
    assertThat(PluginUtils.isPluginInstalled("com.google.idea.bazel.aswb")).isTrue();
  }

  @Ignore("b/337933435")
  @Test
  public void testPluginLoadsWithoutNdkPlugins() {
    assertThat(PluginUtils.isPluginEnabled("com.android.tools.ndk")).isFalse();
    assertThat(PluginUtils.isPluginEnabled("com.google.idea.bazel.aswb")).isTrue();

    // Plugins with classloader failures can be spuriously marked as enabled. Also test that
    // ASwB plugin classes are actually loaded.
    assertThat(BlazeAndroidUserSettings.getInstance()).isNotNull();
  }
}
