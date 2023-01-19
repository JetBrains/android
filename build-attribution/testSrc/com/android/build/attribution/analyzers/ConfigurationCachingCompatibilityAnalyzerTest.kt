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
import com.android.build.AgpVersionInBuildAttributionTest
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.build.attribution.KnownGradlePluginsService
import com.android.build.attribution.data.GradlePluginsData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.getSuccessfulResult
import com.android.build.attribution.ui.controllers.ConfigurationCacheTestBuildFlowRunner
import com.android.ide.common.gradle.Version
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.testutils.VirtualTimeScheduler
import com.android.testutils.junit4.OldAgpTest
import com.android.testutils.junit4.SeparateOldAgpTestsRule
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionStats
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class ConfigurationCachingCompatibilityAnalyzerTest {
  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @get:Rule
  val separateOldAgpTestsRule = SeparateOldAgpTestsRule()

  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
  }

  private fun projectSetup(
    dependencies: String = "",
    pluginsApply: String = "",
    pluginsSectionInRoot: String = "",
    useNewPluginsDsl: Boolean = false,
    entryInGradleProperties: Boolean? = null,
    agpVersion: AgpVersionInBuildAttributionTest = AgpVersionInBuildAttributionTest.CURRENT,
  ) {
    myProjectRule.load(TestProjectPaths.SIMPLE_APPLICATION, agpVersion = agpVersion) { projectRoot ->
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
    projectSetup(
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$KOTLIN_VERSION_FOR_TESTS\"",
      pluginsApply = "apply plugin: 'kotlin-android'"
    )

    val result = runBuildAndGetAnalyzerResult()
    assertThat(result).isInstanceOf(NoIncompatiblePlugins::class.java)
  }

  @Test
  @OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["7.5"])
  fun testOldKotlinDetected() {
    projectSetup(
      agpVersion = AgpVersionInBuildAttributionTest.AGP_71_GRADLE_75,
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "apply plugin: 'kotlin-android'"
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = Version.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @Ignore("Need additional work to make such setup run offline.")
  @OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["LATEST"])
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
      agpVersion = AgpVersionInBuildAttributionTest.AGP_71_GRADLE_75,
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
        currentVersion = Version.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["7.5"])
  @Test
  fun testOldKotlinDetectedAppliedInPluginDslWithExplicitDependency() {
    projectSetup(
      agpVersion = AgpVersionInBuildAttributionTest.AGP_71_GRADLE_75,
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "id 'kotlin-android'",
      useNewPluginsDsl = true
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = Version.parse("1.3.72"),
        pluginInfo = kotlinPluginInfo()
      )))
    }
  }

  @OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["7.5"])
  @Test
  fun testOldKotlinDetectedAppliedAsPluginClass() {
    projectSetup(
      agpVersion = AgpVersionInBuildAttributionTest.AGP_71_GRADLE_75,
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "apply plugin: org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)
    (result as IncompatiblePluginsDetected).upgradePluginWarnings.let { warnings ->
      assertThat(warnings).isEqualTo(listOf(IncompatiblePluginWarning(
        //TODO (mlazeba): discuss in sync:in this case we report the name by which it was applied. Is it correct?
        plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper"),
        currentVersion = Version.parse("1.3.72"),
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

  @Test
  fun testSuccessfulConfigurationCacheTrial() {
    // Simple project with latest (compatible) AGP and without any extra plugins.
    // All should be clean with this setup.
    projectSetup("", "")

    val result = runBuildAndGetAnalyzerResult()
    assertThat(result).isInstanceOf(NoIncompatiblePlugins::class.java)

    val buildRequest = (myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl)
      .currentBuildRequest

    ConfigurationCacheTestBuildFlowRunner.getInstance(myProjectRule.project)
      .scheduleRebuildWithCCOptionAndRunOnSuccess(buildRequest.data, true, {}, {})

    // test metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
      .map { use -> use.studioEvent.buildAttributionStats.let { it.buildType to it.buildAnalysisStatus } }
    assertThat(buildAttributionEvents).isEqualTo(listOf(
      BuildAttributionStats.BuildType.REGULAR_BUILD to BuildAttributionStats.BuildAnalysisStatus.SUCCESS,
      BuildAttributionStats.BuildType.CONFIGURATION_CACHE_TRIAL_FLOW_BUILD to BuildAttributionStats.BuildAnalysisStatus.SUCCESS,
    ))
  }

  @Test
  @OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["7.5"])
  fun testFailedConfigurationCacheTrial() {
    projectSetup(
      agpVersion = AgpVersionInBuildAttributionTest.AGP_71_GRADLE_75,
      dependencies = "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72\"",
      pluginsApply = "apply plugin: 'kotlin-android'"
    )

    val result = runBuildAndGetAnalyzerResult()

    assertThat(result).isInstanceOf(IncompatiblePluginsDetected::class.java)

    val buildRequest = (myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl)
      .currentBuildRequest

    ConfigurationCacheTestBuildFlowRunner.getInstance(myProjectRule.project)
      .scheduleRebuildWithCCOptionAndRunOnSuccess(buildRequest.data, true, {}, {})

    // test metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
      .map { use -> use.studioEvent.buildAttributionStats.let { it.buildType to it.buildAnalysisStatus } }
    assertThat(buildAttributionEvents).isEqualTo(listOf(
      BuildAttributionStats.BuildType.REGULAR_BUILD to BuildAttributionStats.BuildAnalysisStatus.SUCCESS,
      BuildAttributionStats.BuildType.CONFIGURATION_CACHE_TRIAL_FLOW_BUILD to BuildAttributionStats.BuildAnalysisStatus.BUILD_FAILURE,
    ))
  }

  private fun runBuildAndGetAnalyzerResult(): ConfigurationCachingCompatibilityProjectResult {
    myProjectRule.invokeTasksRethrowingErrors("assembleDebug")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getSuccessfulResult()

    return results.getConfigurationCachingCompatibility()
  }

  private fun kotlinPluginInfo(): GradlePluginsData.PluginInfo = ApplicationManager.getApplication()
    .getService(KnownGradlePluginsService::class.java)
    .gradlePluginsData
    .pluginsInfo
    .find { it.pluginArtifact?.name == "kotlin-gradle-plugin" }!!
}