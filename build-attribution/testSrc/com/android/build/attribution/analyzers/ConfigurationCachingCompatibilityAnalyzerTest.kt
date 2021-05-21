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
package com.android.build.attribution.analyzers

import com.android.SdkConstants
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.build.attribution.KnownGradlePluginsService
import com.android.build.attribution.data.GradlePluginsData
import com.android.build.attribution.data.PluginData
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.TestUtils.getKotlinVersionForTests
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class ConfigurationCachingCompatibilityAnalyzerTest {
  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.clearOverride()
  }

  private fun projectSetup(
    dependencies: String = "",
    pluginsApply: String = "",
    pluginsSectionInRoot: String = "",
    useNewPluginsDsl: Boolean = false,
    entryInGradleProperties: Boolean? = null
  ) {
    myProjectRule.load(TestProjectPaths.SIMPLE_APPLICATION) { projectRoot ->
      // Add plugins application to `app/build.gradle`.
      val appBuildFile = FileUtils.join(projectRoot, "app", SdkConstants.FN_BUILD_GRADLE)
      appBuildFile.readText().let { content ->
        val newContent = if (useNewPluginsDsl)
          content.patchApplyWithNewDsl(pluginsApply)
        else content.patchApplyWithOldDsl(pluginsApply)
        FileUtil.writeToFile(appBuildFile, newContent)
      }
      // Add dependencies to buildscript in `./build.gradle`.
      val rootBuildFile = FileUtils.join(projectRoot, SdkConstants.FN_BUILD_GRADLE)
      rootBuildFile.readText().let { content ->
        val newContent = content
          .replace(oldValue = "dependencies {", newValue = "dependencies {\n$dependencies")
          .replace(oldValue = "allprojects {", newValue = "$pluginsSectionInRoot\n\nallprojects {")

        FileUtil.writeToFile(rootBuildFile, newContent)
      }
      if (entryInGradleProperties != null) {
        val propertiesFile = FileUtils.join(projectRoot, SdkConstants.FN_GRADLE_PROPERTIES)
        propertiesFile.readText().let { content ->
          val newContent = "$content\norg.gradle.unsafe.configuration-cache=$entryInGradleProperties"
          FileUtil.writeToFile(propertiesFile, newContent)
        }
      }
    }
  }

  private fun String.patchApplyWithOldDsl(pluginsApply: String) = this.replace(
    oldValue = "apply plugin: 'com.android.application'",
    newValue = """
          apply plugin: 'com.android.application'
          $pluginsApply
        """.trimIndent())

  private fun String.patchApplyWithNewDsl(pluginsApply: String) = this.replace(
    oldValue = "apply plugin: 'com.android.application'",
    newValue = """
      plugins {
          id 'com.android.application'
          $pluginsApply
      }
        """.trimIndent())

  @Test
  fun testProjectWithLatestAGPOnly() {
    // Simple project with latest (compatible) AGP and without any extra plugins.
    // All should be clean with this setup.
    projectSetup("", "")

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(NoIncompatiblePlugins::class.java)
  }


  @Test
  fun testNewKotlinNotDetected() {
    val kotlinVersion = getKotlinVersionForTests()
    projectSetup(
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion\"",
      pluginsApply = "apply plugin: 'kotlin-android'"
    )

    val result = runBuildAndGetAnalyzerResult()
    assertThat(result).isInstanceOf(NoIncompatiblePlugins::class.java)
  }

  @Test
  fun testOldKotlinDetected() {
    projectSetup(
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "apply plugin: 'kotlin-android'"
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = GradleVersion.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @Ignore("Need additional work to make such setup run offline.")
  @Test
  fun testOldKotlinDetectedAppliedInPluginDsl() {
    /*
    TODO need to add the following to make this test work
      1) instruct gradle to look for plugins in local repository, not Gradle Plugin Portal.
         Doc: https://docs.gradle.org/current/userguide/plugins.html#sec:custom_plugin_repositories
      1.1) Add to settings.gradle 'pluginManagement { repositories { maven url '<prebuilts>' } }' section
           (Probably Use AndroidGradleTests.getLocalRepositoriesForGroovy() call to populate the repository)
      1.2) Add plugin marker artifact for the required plugin https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_markers
     */

    projectSetup(
      dependencies = "",
      pluginsApply = "id 'org.jetbrains.kotlin.android'",
      pluginsSectionInRoot = "plugins { id 'org.jetbrains.kotlin.android' version '1.3.72' apply false }",
      useNewPluginsDsl = true
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = GradleVersion.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @Test
  fun testOldKotlinDetectedAppliedInPluginDslWithExplicitDependency() {
    projectSetup(
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "id 'kotlin-android'",
      useNewPluginsDsl = true
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = GradleVersion.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @Test
  fun testOldKotlinDetectedAppliedAsPluginClass() {
    projectSetup(
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "apply plugin: org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        //TODO (mlazeba): discuss in sync:in this case we report the name by which it was applied. Is it correct?
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = GradleVersion.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @Test
  fun testSimpleProjectWithCCTurnedOn() {
    projectSetup("", "", entryInGradleProperties = true)

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(ConfigurationCachingTurnedOn::class.java)
  }

  @Test
  fun testSimpleProjectWithCCTurnedOff() {
    projectSetup("", "", entryInGradleProperties = false)

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(ConfigurationCachingTurnedOff::class.java)
  }

  private fun runBuildAndGetAnalyzerResult(): ConfigurationCachingCompatibilityProjectResult {
    val invocationResult = myProjectRule.invokeTasks("assembleDebug")
    assertThat(invocationResult.isBuildSuccessful).isTrue()

    return (ServiceManager.getService(
      myProjectRule.project,
      BuildAttributionManager::class.java
    ) as BuildAttributionManagerImpl).analyzersProxy.getConfigurationCachingCompatibility()
  }

  private fun kotlinPluginInfo(): GradlePluginsData.PluginInfo = ServiceManager
    .getService(KnownGradlePluginsService::class.java)
    .gradlePluginsData
    .pluginsInfo
    .find { it.pluginArtifact?.name == "kotlin-gradle-plugin" }!!
}