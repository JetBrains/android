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

import com.android.build.attribution.data.GradlePluginsData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.ide.common.repository.GradleVersion
import com.google.common.truth.Truth
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.junit.Test
import org.mockito.Mockito

class ConfigurationCachingCompatibilityAnalyzerUnitTest {

  val compatiblePlugin = GradlePluginsData.PluginInfo(
    name = "Compatible Plugin",
    pluginClasses = listOf("my.org.gradle.Plugin1"),
    pluginArtifact = GradlePluginsData.DependencyCoordinates("my.org", "plugin1-jar"),
    configurationCachingCompatibleFrom = GradleVersion.parse("0.2.0")
  )
  val incompatiblePlugin = GradlePluginsData.PluginInfo(
    name = "Incompatible Plugin",
    pluginClasses = listOf("my.org.gradle.Plugin2"),
    pluginArtifact = GradlePluginsData.DependencyCoordinates("my.org", "plugin2-jar")
  )
  val knownPluginsData = GradlePluginsData(pluginsInfo = listOf(compatiblePlugin, incompatiblePlugin))

  val pluginContainer = PluginContainer()

  @Test
  fun `AGP compatible, no other plugins`() = test(TestCase(
    agpVersion = GradleVersion.parse("4.2.0"),
    pluginsApplied = listOf(binaryPlugin("com.android.build.gradle.AppPlugin")),
    buildSrcDependenciesInfo = emptySet(),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = NoIncompatiblePlugins
  ))

  @Test
  fun `AGP incompatible, no other plugins`() = test(TestCase(
    agpVersion = GradleVersion.parse("4.1.0"),
    pluginsApplied = listOf(binaryPlugin("com.android.build.gradle.AppPlugin")),
    buildSrcDependenciesInfo = emptySet(),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = AGPUpdateRequired(GradleVersion.parse("4.1.0"))
  ))

  @Test
  fun `No applied plugins, no buildscript dependencies, no known data`() = test(TestCase(
    pluginsApplied = listOf(),
    buildSrcDependenciesInfo = emptySet(),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = NoIncompatiblePlugins
  ))

  @Test
  fun `No applied plugins, no buildscript dependencies`() = test(TestCase(
    pluginsApplied = listOf(),
    buildSrcDependenciesInfo = emptySet(),
    knownPluginsData = knownPluginsData,
    expectedResult = NoIncompatiblePlugins
  ))

  @Test
  fun `buildscript has dependency but no applied plugins, no plugins known`() = test(TestCase(
    pluginsApplied = listOf(),
    buildSrcDependenciesInfo = setOf("my.org:plugin-jar:0.1.0"),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = NoIncompatiblePlugins
  ))

  @Test
  fun `buildscript has dependency of known compatible but not applied plugin`() = test(TestCase(
    pluginsApplied = listOf(),
    buildSrcDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    // No incompatible since plugin not applied.
    expectedResult = NoIncompatiblePlugins
  ))

  @Test
  fun `buildscript has dependency of known incompatible but not applied plugin`() = test(TestCase(
    pluginsApplied = listOf(),
    buildSrcDependenciesInfo = setOf("my.org:plugin2-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    // No incompatible since plugin not applied.
    expectedResult = NoIncompatiblePlugins
  ))

  @Test
  fun `buildscript has dependency of unknown applied plugin`() = test(TestCase(
    pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin1")),
    buildSrcDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
    knownPluginsData = GradlePluginsData.emptyData,
    // No incompatible since plugin since not in known.
    expectedResult = NoIncompatiblePlugins(listOf(binaryPlugin("my.org.gradle.Plugin1")))
  ))

  @Test
  fun `buildscript has dependency of known incompatible applied plugin`() = test(TestCase(
    pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin2")),
    buildSrcDependenciesInfo = setOf("my.org:plugin2-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    expectedResult = IncompatiblePluginsDetected(
      incompatiblePluginWarnings = listOf(IncompatiblePluginWarning(
        plugin = binaryPlugin("my.org.gradle.Plugin2"),
        currentVersion = GradleVersion.parse("0.1.0"),
        pluginInfo = incompatiblePlugin
      )),
      upgradePluginWarnings = emptyList()
    )
  ))

  @Test
  fun `buildscript has lower version dependency of known compatible applied plugin`() = test(TestCase(
    pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin1")),
    buildSrcDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    expectedResult = IncompatiblePluginsDetected(
      incompatiblePluginWarnings = emptyList(),
      upgradePluginWarnings = listOf(IncompatiblePluginWarning(
        plugin = binaryPlugin("my.org.gradle.Plugin1"),
        currentVersion = GradleVersion.parse("0.1.0"),
        pluginInfo = compatiblePlugin
      ))
    )
  ))

  @Test
  fun `buildscript has dependency of known compatible applied plugin`() {
    test(
      TestCase(
        pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin1")),
        buildSrcDependenciesInfo = setOf("my.org:plugin1-jar:0.2.0"),
        knownPluginsData = knownPluginsData,
        expectedResult = NoIncompatiblePlugins
      ),
    )
  }

  private fun test(testCaseData: TestCase) {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val analysisResult = Mockito.mock(BuildEventsAnalysisResult::class.java)
    val studioProvidedInfo = StudioProvidedInfo(
      agpVersion = testCaseData.agpVersion,
      //TODO add test for true
      configurationCachingGradlePropertyState = "false"
    )
    Mockito.`when`(analysisResult.getAppliedPlugins()).thenReturn(mapOf(":" to testCaseData.pluginsApplied))
    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = testCaseData.buildSrcDependenciesInfo)
    )
    analyzer.receiveKnownPluginsData(testCaseData.knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(testCaseData.expectedResult)
  }

  @Test
  fun testPluginAppliedInSeveralProjects() {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val studioProvidedInfo = StudioProvidedInfo(
      agpVersion = GradleVersion.parse("4.2.0"),
      configurationCachingGradlePropertyState = "false"
    )
    val analysisResult = Mockito.mock(BuildEventsAnalysisResult::class.java)

    Mockito.`when`(analysisResult.getAppliedPlugins()).thenReturn(mapOf(
      ":app" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":app")),
      ":lib" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":lib")),
    ))
    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"))
    )
    analyzer.receiveKnownPluginsData(knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(IncompatiblePluginsDetected(
      incompatiblePluginWarnings = emptyList(),
      upgradePluginWarnings = listOf(IncompatiblePluginWarning(
        plugin = binaryPlugin("my.org.gradle.Plugin1"),
        currentVersion = GradleVersion.parse("0.1.0"),
        pluginInfo = compatiblePlugin
      ))
    ))
  }

  @Test
  fun testWhenCCFlagIsAlreadyOn() {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val studioProvidedInfo = StudioProvidedInfo(
      agpVersion = GradleVersion.parse("4.2.0"),
      configurationCachingGradlePropertyState = "true"
    )
    val analysisResult = Mockito.mock(BuildEventsAnalysisResult::class.java)

    Mockito.`when`(analysisResult.getAppliedPlugins()).thenReturn(mapOf(
      ":app" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":app")),
    ))
    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.2.0"))
    )
    analyzer.receiveKnownPluginsData(knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(ConfigurationCachingTurnedOn)
  }

  private fun binaryPlugin(pluginClassName: String, projectPath: String = ":") = pluginContainer
    .getPlugin(object : BinaryPluginIdentifier{
      override fun getDisplayName(): String = pluginClassName
      override fun getClassName(): String = pluginClassName
      override fun getPluginId(): String = pluginClassName
    }, projectPath)

  data class TestCase(
    val agpVersion: GradleVersion = GradleVersion.parse("4.2.0"),
    val pluginsApplied: List<PluginData>,
    val buildSrcDependenciesInfo: Set<String>,
    val knownPluginsData: GradlePluginsData,
    val expectedResult: ConfigurationCachingCompatibilityProjectResult
  )
}
