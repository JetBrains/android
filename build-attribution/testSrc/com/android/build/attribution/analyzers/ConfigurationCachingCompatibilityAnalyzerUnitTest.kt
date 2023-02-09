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

import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.GradlePluginsData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito

@RunWith(Parameterized::class)
class ConfigurationCachingCompatibilityAnalyzerUnitTest(
  private val gradleVersion: GradleVersion?,
  private val gradleWithStableConfigurationCache: Boolean
) {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Testing with Gradle version \"{0}\"")
    fun data() = arrayOf<Array<Any?>>(
      arrayOf(GradleVersion.version("8.0.0"), false),
      arrayOf(GradleVersion.version("8.1"), true),
      arrayOf(null, false),
    )
  }

  val compatiblePlugin = GradlePluginsData.PluginInfo(
    name = "Compatible Plugin",
    pluginClasses = listOf("my.org.gradle.Plugin1"),
    pluginArtifact = GradlePluginsData.DependencyCoordinates("my.org", "plugin1-jar"),
    configurationCachingCompatibleFrom = Version.parse("0.2.0")
  )
  val incompatiblePlugin = GradlePluginsData.PluginInfo(
    name = "Incompatible Plugin",
    pluginClasses = listOf("my.org.gradle.Plugin2"),
    pluginArtifact = GradlePluginsData.DependencyCoordinates("my.org", "plugin2-jar")
  )
  val knownPluginsData = GradlePluginsData(pluginsInfo = listOf(compatiblePlugin, incompatiblePlugin))

  val pluginContainer = PluginContainer()

  private val appPluginData = binaryPlugin("com.android.build.gradle.AppPlugin")

  @Test
  fun `AGP compatible, no other plugins`() = test(TestCase(
    agpVersion = AgpVersion.parse("7.0.0"),
    pluginsApplied = listOf(appPluginData),
    buildscriptDependenciesInfo = emptySet(),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `AGP incompatible, no other plugins`() = test(TestCase(
    agpVersion = AgpVersion.parse("4.1.0"),
    pluginsApplied = listOf(appPluginData),
    buildscriptDependenciesInfo = emptySet(),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = AGPUpdateRequired(AgpVersion.parse("4.1.0"), listOf(appPluginData))
  ))

  @Test
  fun `No applied plugins, no buildscript dependencies, no known data`() = test(TestCase(
    pluginsApplied = listOf(),
    buildscriptDependenciesInfo = emptySet(),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `No applied plugins, no buildscript dependencies`() = test(TestCase(
    pluginsApplied = listOf(),
    buildscriptDependenciesInfo = emptySet(),
    knownPluginsData = knownPluginsData,
    expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `buildscript has dependency but no applied plugins, no plugins known`() = test(TestCase(
    pluginsApplied = listOf(),
    buildscriptDependenciesInfo = setOf("my.org:plugin-jar:0.1.0"),
    knownPluginsData = GradlePluginsData.emptyData,
    expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `buildscript has dependency of known compatible but not applied plugin`() = test(TestCase(
    pluginsApplied = listOf(),
    buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    // No incompatible since plugin not applied.
    expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `buildscript has dependency of known incompatible but not applied plugin`() = test(TestCase(
    pluginsApplied = listOf(),
    buildscriptDependenciesInfo = setOf("my.org:plugin2-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    // No incompatible since plugin not applied.
    expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `buildscript has dependency of unknown applied plugin`() = test(TestCase(
    pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin1")),
    buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
    knownPluginsData = GradlePluginsData.emptyData,
    // No incompatible since plugin since not in known.
    expectedResult = NoIncompatiblePlugins(listOf(binaryPlugin("my.org.gradle.Plugin1")), gradleWithStableConfigurationCache)
  ))

  @Test
  fun `buildscript has dependency of known incompatible applied plugin`() = test(TestCase(
    pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin2")),
    buildscriptDependenciesInfo = setOf("my.org:plugin2-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    expectedResult = IncompatiblePluginsDetected(
      incompatiblePluginWarnings = listOf(IncompatiblePluginWarning(
        plugin = binaryPlugin("my.org.gradle.Plugin2"),
        currentVersion = Version.parse("0.1.0"),
        pluginInfo = incompatiblePlugin
      )),
      upgradePluginWarnings = emptyList()
    )
  ))

  @Test
  fun `buildscript has lower version dependency of known compatible applied plugin`() = test(TestCase(
    pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin1")),
    buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
    knownPluginsData = knownPluginsData,
    expectedResult = IncompatiblePluginsDetected(
      incompatiblePluginWarnings = emptyList(),
      upgradePluginWarnings = listOf(IncompatiblePluginWarning(
        plugin = binaryPlugin("my.org.gradle.Plugin1"),
        currentVersion = Version.parse("0.1.0"),
        pluginInfo = compatiblePlugin
      ))
    )
  ))

  @Test
  fun `buildscript has dependency of known compatible applied plugin`() {
    test(
      TestCase(
        pluginsApplied = listOf(binaryPlugin("my.org.gradle.Plugin1")),
        buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.2.0"),
        knownPluginsData = knownPluginsData,
        expectedResult = NoIncompatiblePlugins(emptyList(), gradleWithStableConfigurationCache)
      ),
    )
  }

  private fun test(testCaseData: TestCase) {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)
    val pluginAnalyzer = Mockito.mock(ProjectConfigurationAnalyzer::class.java)
    val studioProvidedInfo = studioProvidedInfo(
      agpVersion = testCaseData.agpVersion,
      configurationCachingGradlePropertyState = null,
      buildInvocationType = BuildInvocationType.REGULAR_BUILD
    )
    whenever(analysisResult.projectConfigurationAnalyzer).thenReturn(pluginAnalyzer)
    whenever(pluginAnalyzer.result).thenReturn(ProjectConfigurationAnalyzer.Result(emptyMap(), emptyList(), mapOf(":" to testCaseData.pluginsApplied)))
    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = testCaseData.buildscriptDependenciesInfo,
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(testCaseData.agpVersion.toString(), false)
    ))
    analyzer.receiveKnownPluginsData(testCaseData.knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(testCaseData.expectedResult)
  }

  @Test
  fun testPluginAppliedInSeveralProjects() {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val agpVersionString = "7.0.0"
    val studioProvidedInfo = studioProvidedInfo(
      agpVersion = AgpVersion.parse(agpVersionString),
      configurationCachingGradlePropertyState = null,
      buildInvocationType = BuildInvocationType.REGULAR_BUILD
    )
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)
    val pluginAnalyzer = Mockito.mock(ProjectConfigurationAnalyzer::class.java)
    whenever(analysisResult.projectConfigurationAnalyzer).thenReturn(pluginAnalyzer)
    whenever(pluginAnalyzer.result).thenReturn(ProjectConfigurationAnalyzer.Result(emptyMap(), emptyList(), mapOf(
      ":app" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":app")),
      ":lib" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":lib"))
    )))

    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.1.0"),
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(agpVersionString, false)
    ))
    analyzer.receiveKnownPluginsData(knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(IncompatiblePluginsDetected(
      incompatiblePluginWarnings = emptyList(),
      upgradePluginWarnings = listOf(IncompatiblePluginWarning(
        plugin = binaryPlugin("my.org.gradle.Plugin1"),
        currentVersion = Version.parse("0.1.0"),
        pluginInfo = compatiblePlugin
      ))
    ))
  }

  @Test
  fun testWhenCCFlagIsAlreadyOn() {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val agpVersionString = "7.0.0"
    val studioProvidedInfo = studioProvidedInfo(
      agpVersion = AgpVersion.parse(agpVersionString),
      configurationCachingGradlePropertyState = "true",
      buildInvocationType = BuildInvocationType.REGULAR_BUILD
    )
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)
    val pluginAnalyzer = Mockito.mock(ProjectConfigurationAnalyzer::class.java)
    whenever(analysisResult.projectConfigurationAnalyzer).thenReturn(pluginAnalyzer)
    whenever(pluginAnalyzer.result).thenReturn(ProjectConfigurationAnalyzer.Result(emptyMap(), emptyList(), mapOf(
      ":app" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":app")))))

    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.2.0"),
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(agpVersionString, true)
    ))
    analyzer.receiveKnownPluginsData(knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(ConfigurationCachingTurnedOn)
  }

  @Test
  fun testWhenCCFlagIsAlreadyOff() {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val agpVersionString = "7.0.0"
    val studioProvidedInfo = studioProvidedInfo(
      agpVersion = AgpVersion.parse(agpVersionString),
      configurationCachingGradlePropertyState = "false",
      buildInvocationType = BuildInvocationType.REGULAR_BUILD
    )
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)
    val pluginAnalyzer = Mockito.mock(ProjectConfigurationAnalyzer::class.java)
    whenever(analysisResult.projectConfigurationAnalyzer).thenReturn(pluginAnalyzer)
    whenever(pluginAnalyzer.result).thenReturn(ProjectConfigurationAnalyzer.Result(emptyMap(), emptyList(), mapOf(
      ":app" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":app")))))

    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.2.0"),
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(agpVersionString, false)
    ))
    analyzer.receiveKnownPluginsData(knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(ConfigurationCachingTurnedOff)
  }

  @Test
  fun testWhenRunningConfigurationCacheCompatibilityTestBuild() {
    val analyzer = ConfigurationCachingCompatibilityAnalyzer()
    val agpVersionString = "7.0.0"
    val studioProvidedInfo = studioProvidedInfo(
      agpVersion = AgpVersion.parse(agpVersionString),
      configurationCachingGradlePropertyState = null,
      buildInvocationType = BuildInvocationType.CONFIGURATION_CACHE_TRIAL
    )
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)
    val pluginAnalyzer = Mockito.mock(ProjectConfigurationAnalyzer::class.java)
    whenever(analysisResult.projectConfigurationAnalyzer).thenReturn(pluginAnalyzer)
    whenever(pluginAnalyzer.result).thenReturn(ProjectConfigurationAnalyzer.Result(emptyMap(), emptyList(), mapOf(
      ":app" to listOf(binaryPlugin("my.org.gradle.Plugin1", ":app")))))

    analyzer.receiveBuildAttributionReport(AndroidGradlePluginAttributionData(
      buildscriptDependenciesInfo = setOf("my.org:plugin1-jar:0.2.0"),
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(agpVersionString, true)
    ))
    analyzer.receiveKnownPluginsData(knownPluginsData)
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(ConfigurationCacheCompatibilityTestFlow(gradleWithStableConfigurationCache))
  }

  private fun binaryPlugin(pluginClassName: String, projectPath: String = ":", displayName: String = pluginClassName) = pluginContainer
    .getPlugin(object : BinaryPluginIdentifier {
      override fun getDisplayName(): String = displayName
      override fun getClassName(): String = pluginClassName
      override fun getPluginId(): String = pluginClassName
    }, projectPath)

  private fun studioProvidedInfo(
    agpVersion: AgpVersion?,
    configurationCachingGradlePropertyState: String?,
    buildInvocationType: BuildInvocationType
    ): StudioProvidedInfo = StudioProvidedInfo(
    agpVersion = agpVersion,
    gradleVersion = gradleVersion,
    configurationCachingGradlePropertyState = configurationCachingGradlePropertyState,
    buildInvocationType = buildInvocationType,
    enableJetifierPropertyState = false,
    useAndroidXPropertyState = false,
    buildRequestHolder = mock()
  )

  data class TestCase(
    val agpVersion: AgpVersion = AgpVersion.parse("7.0.0"),
    val pluginsApplied: List<PluginData>,
    val buildscriptDependenciesInfo: Set<String>,
    val knownPluginsData: GradlePluginsData,
    val expectedResult: ConfigurationCachingCompatibilityProjectResult
  )
}
