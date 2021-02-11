/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution.data

import com.android.build.attribution.analyzers.createBinaryPluginIdentifierStub
import com.android.build.attribution.analyzers.createScriptPluginIdentifierStub
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class PluginContainerTest {

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  val pluginContainer = PluginContainer()

  @Test
  fun testBinaryPluginCreated() {
    val binaryPlugin = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val plugin = pluginContainer.getPlugin(binaryPlugin, ":app")
    Truth.assertThat(plugin.pluginType).isEqualTo(PluginData.PluginType.BINARY_PLUGIN)
    Truth.assertThat(plugin.idName).isEqualTo("my.gradle.plugin.PluginA")
    Truth.assertThat(plugin.displayName).isEqualTo("pluginA")
  }

  @Test
  fun testBinaryPluginFoundByName() {
    val binaryPlugin = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val createdPlugin = pluginContainer.getPlugin(binaryPlugin, ":app")
    val foundPlugin = pluginContainer.findPluginByName("pluginA", ":app")
    Truth.assertThat(foundPlugin).isSameAs(createdPlugin)
  }

  @Test
  fun testScriptPluginCreated() {
    val scriptPlugin = createScriptPluginIdentifierStub("build.gradle")
    val plugin = pluginContainer.getPlugin(scriptPlugin, ":app")
    Truth.assertThat(plugin.pluginType).isEqualTo(PluginData.PluginType.SCRIPT)
    Truth.assertThat(plugin.idName).isEqualTo(":app:build.gradle")
    Truth.assertThat(plugin.displayName).isEqualTo(":app:build.gradle")
  }

  @Test
  fun testScriptPluginFoundByName() {
    val scriptPlugin = createScriptPluginIdentifierStub("build.gradle")
    val createdPlugin = pluginContainer.getPlugin(scriptPlugin, ":app")
    val foundPlugin = pluginContainer.findPluginByName(":app:build.gradle", ":app")
    Truth.assertThat(foundPlugin).isSameAs(createdPlugin)
  }

  @Test
  fun testPluginRequestedSeveralTimesHasSameEntry() {
    val binaryPlugin = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val pluginData1 = pluginContainer.getPlugin(binaryPlugin, ":app")
    val pluginData2 = pluginContainer.getPlugin(binaryPlugin, ":app")

    Truth.assertThat(pluginData1).isSameAs(pluginData2)
    Truth.assertThat(pluginData1.displayName).isEqualTo("pluginA")
  }

  @Test
  fun testPluginRequestedSeveralTimesInDifferentProjectsHasSameEntry() {
    val binaryPlugin = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val pluginData1 = pluginContainer.getPlugin(binaryPlugin, ":app")
    val pluginData2 = pluginContainer.getPlugin(binaryPlugin, ":lib")

    Truth.assertThat(pluginData1).isSameAs(pluginData2)
    Truth.assertThat(pluginData1.displayName).isEqualTo("pluginA")
  }

  @Test
  fun testPluginRequestedSeveralTimesInDifferentProjectsByDifferentNamesHasSameEntry() {
    val binaryPluginId1 = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val binaryPluginId2 = createBinaryPluginIdentifierStub("my.gradle.pluginA", "my.gradle.plugin.PluginA")
    val pluginData1 = pluginContainer.getPlugin(binaryPluginId1, ":app")
    val pluginData2 = pluginContainer.getPlugin(binaryPluginId2, ":lib")

    Truth.assertThat(pluginData1).isSameAs(pluginData2)
    Truth.assertThat(pluginData1.displayNameInProject(":app")).isEqualTo("pluginA")
    Truth.assertThat(pluginData1.displayNameInProject(":lib")).isEqualTo("my.gradle.pluginA")
    Truth.assertThat(pluginData1.displayName).isEqualTo("pluginA")
  }

  @Test
  fun testAGPPluginDetected() {
    fun plugin(id: String, displayName: String = id): PluginData {
      val pluginId = createBinaryPluginIdentifierStub(displayName, id)
      return pluginContainer.getPlugin(pluginId, ":app")
    }
    fun testThat(id: String, displayName: String = id, isAndroidPlugin: Boolean) = plugin(id, displayName)
        .let { this.expect.withMessage("$id isAndroidPlugin").that(it.isAndroidPlugin()).isEqualTo(isAndroidPlugin)}
    testThat("com.android.build.gradle.api.AndroidBasePlugin", "com.android.base", isAndroidPlugin = true)
    testThat("com.android.build.gradle.AppPlugin", "com.android.application", isAndroidPlugin = true)
    testThat("com.android.build.gradle.AppPlugin", "android", isAndroidPlugin = true)
    testThat("com.android.build.gradle.AssetOnlyBundlePlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.AssetPackPlugin", "com.android.asset-pack", isAndroidPlugin = true)
    testThat("com.android.build.gradle.DynamicFeaturePlugin", "com.android.dynamic-feature", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.AppPlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.AssetOnlyBundlePlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.AssetPackPlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.DynamicFeaturePlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.LibraryPlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.ReportingPlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.TestPlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.internal.plugins.VersionCheckPlugin", isAndroidPlugin = true)
    testThat("com.android.build.gradle.LibraryPlugin", "android-library", isAndroidPlugin = true)
    testThat("com.android.build.gradle.LibraryPlugin", "com.android.library", isAndroidPlugin = true)
    testThat("com.android.build.gradle.LintPlugin", "com.android.lint", isAndroidPlugin = true)
    testThat("com.android.build.gradle.LintPlugin\$Inject", isAndroidPlugin = true)
    testThat("com.android.build.gradle.ReportingPlugin", "android-reporting", isAndroidPlugin = true)
    testThat("com.android.build.gradle.ReportingPlugin", "com.android.reporting", isAndroidPlugin = true)
    testThat("com.android.build.gradle.TestPlugin", "com.android.test", isAndroidPlugin = true)

    //These are not AGP related so should return false
    testThat("com.android.ide.gradle.model.builder.AndroidStudioToolingPlugin", isAndroidPlugin = false)
    testThat("com.android.java.model.builder.JavaLibraryPlugin", isAndroidPlugin = false)
  }
}