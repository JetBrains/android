/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.PluginData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class ProjectConfigurationAnalyzerTest {

  private val warningsFilter = BuildAttributionWarningsFilter()
  private val analyzer = ProjectConfigurationAnalyzer(warningsFilter)

  private val androidGradlePlugin = createBinaryPluginIdentifierStub("com.android.application")
  private val pluginA = createBinaryPluginIdentifierStub("pluginA")
  private val pluginB = createBinaryPluginIdentifierStub("pluginB")
  private val pluginC = createBinaryPluginIdentifierStub("pluginC")
  private val buildScript = createScriptPluginIdentifierStub("build.gradle")

  private fun sendProjectConfigurationEventsToAnalyzer() {
    analyzer.onBuildStart()

    analyzer.receiveEvent(createProjectConfigurationFinishEventStub(":app", listOf(
      Pair(androidGradlePlugin, Duration.ofMillis(300)),
      Pair(pluginA, Duration.ofMillis(400)),
      Pair(pluginB, Duration.ofMillis(40)),
      Pair(pluginC, Duration.ofMillis(350)),
      Pair(buildScript, Duration.ofMillis(200))), 0, 1290))

    analyzer.receiveEvent(createProjectConfigurationFinishEventStub(":lib", listOf(
      Pair(androidGradlePlugin, Duration.ofMillis(200)),
      Pair(pluginA, Duration.ofMillis(150)),
      Pair(pluginB, Duration.ofMillis(30)),
      Pair(pluginC, Duration.ofMillis(300)),
      Pair(buildScript, Duration.ofMillis(250))), 0, 830))

    analyzer.onBuildSuccess()
  }

  @Test
  fun testProjectConfigurationAnalyzer() {
    sendProjectConfigurationEventsToAnalyzer()

    assertThat(analyzer.pluginsSlowingConfiguration).hasSize(2)

    assertThat(analyzer.pluginsSlowingConfiguration[0].project).isEqualTo(":app")
    assertThat(analyzer.pluginsSlowingConfiguration[0].pluginsConfigurationData).containsExactlyElementsIn(
      listOf(ProjectConfigurationAnalyzer.PluginConfigurationData(PluginData(pluginA), Duration.ofMillis(400)),
             ProjectConfigurationAnalyzer.PluginConfigurationData(PluginData(pluginC), Duration.ofMillis(350))))

    assertThat(analyzer.pluginsSlowingConfiguration[1].project).isEqualTo(":lib")
    assertThat(analyzer.pluginsSlowingConfiguration[1].pluginsConfigurationData).containsExactlyElementsIn(
      listOf(ProjectConfigurationAnalyzer.PluginConfigurationData(PluginData(pluginC), Duration.ofMillis(300)),
             ProjectConfigurationAnalyzer.PluginConfigurationData(PluginData(buildScript), Duration.ofMillis(250))))
  }

  @Test
  fun testProjectConfigurationAnalyzerWithSuppressedWarnings() {
    warningsFilter.suppressWarningsForPlugin(pluginC.displayName)

    sendProjectConfigurationEventsToAnalyzer()

    assertThat(analyzer.pluginsSlowingConfiguration).hasSize(2)

    assertThat(analyzer.pluginsSlowingConfiguration[0].project).isEqualTo(":app")
    assertThat(analyzer.pluginsSlowingConfiguration[0].pluginsConfigurationData).containsExactlyElementsIn(
      listOf(ProjectConfigurationAnalyzer.PluginConfigurationData(PluginData(pluginA), Duration.ofMillis(400))))

    assertThat(analyzer.pluginsSlowingConfiguration[1].project).isEqualTo(":lib")
    assertThat(analyzer.pluginsSlowingConfiguration[1].pluginsConfigurationData).containsExactlyElementsIn(
      listOf(ProjectConfigurationAnalyzer.PluginConfigurationData(PluginData(buildScript), Duration.ofMillis(250))))
  }
}
