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
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskContainer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class ProjectConfigurationAnalyzerTest {

  private val warningsFilter = BuildAttributionWarningsFilter()
  private val analyzer = ProjectConfigurationAnalyzer(warningsFilter, TaskContainer(), PluginContainer())

  private val applicationPlugin = createBinaryPluginIdentifierStub("com.android.application")
  private val libraryPlugin = createBinaryPluginIdentifierStub("com.android.library")
  private val pluginA = createBinaryPluginIdentifierStub("pluginA")
  private val pluginB = createBinaryPluginIdentifierStub("pluginB")
  private val pluginC = createBinaryPluginIdentifierStub("pluginC")
  private val buildScriptA = createScriptPluginIdentifierStub("buildA.gradle")
  private val buildScriptB = createScriptPluginIdentifierStub("buildB.gradle")

  private fun sendProjectConfigurationEventsToAnalyzer() {
    analyzer.onBuildStart()

    // The following events represent this plugins tree
    //
    // plugin pluginA (0.5 sec) *SLOW*
    // script :app:buildA.gradle (1 sec) {
    //   plugin com.android.application (0.25 sec)
    //   plugin pluginB (0.1 sec)
    //   script :app:buildB.gradle (0.45 sec) {
    //     plugin pluginC (0.4 sec) *SLOW*
    //   }
    // }
    analyzer.receiveEvent(createProjectConfigurationStartEventStub(":app"))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${pluginA.displayName} to project :app started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${pluginA.displayName} to project :app finished", 0, 500))
    analyzer.receiveEvent(createStartEventStub("Apply script ${buildScriptA.displayName} to project :app started"))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${applicationPlugin.displayName} to project :app started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${applicationPlugin.displayName} to project :app finished", 600, 850))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${pluginB.displayName} to project :app started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${pluginB.displayName} to project :app finished", 900, 1000))
    analyzer.receiveEvent(createStartEventStub("Apply script ${buildScriptB.displayName} to project :app started"))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${pluginC.displayName} to project :app started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${pluginC.displayName} to project :app finished", 1100, 1500))
    analyzer.receiveEvent(createFinishEventStub("Apply script ${buildScriptB.displayName} to project :app finished", 1050, 1500))
    analyzer.receiveEvent(createFinishEventStub("Apply script ${buildScriptA.displayName} to project :app finished", 500, 1500))
    analyzer.receiveEvent(createProjectConfigurationFinishEventStub(":app", 0, 1600))

    // The following events represent this plugins tree
    //
    // plugin pluginA (0.1 sec)
    // script :lib:buildA.gradle (0.8 sec) {
    //   plugin com.android.library (0.15 sec)
    //   plugin pluginB (0.25 sec) *SLOW*
    //   script :lib:buildB.gradle (0.2 sec) {
    //     plugin pluginC (0.1 sec)
    //   }
    // }
    analyzer.receiveEvent(createProjectConfigurationStartEventStub(":lib"))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${pluginA.displayName} to project :lib started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${pluginA.displayName} to project :lib finished", 0, 100))
    analyzer.receiveEvent(createStartEventStub("Apply script ${buildScriptA.displayName} to project :lib started"))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${libraryPlugin.displayName} to project :lib started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${libraryPlugin.displayName} to project :lib finished", 200, 350))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${pluginB.displayName} to project :lib started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${pluginB.displayName} to project :lib finished", 350, 600))
    analyzer.receiveEvent(createStartEventStub("Apply script ${buildScriptB.displayName} to project :lib started"))
    analyzer.receiveEvent(createStartEventStub("Apply plugin ${pluginC.displayName} to project :lib started"))
    analyzer.receiveEvent(createFinishEventStub("Apply plugin ${pluginC.displayName} to project :lib finished", 700, 800))
    analyzer.receiveEvent(createFinishEventStub("Apply script ${buildScriptB.displayName} to project :lib finished", 650, 850))
    analyzer.receiveEvent(createFinishEventStub("Apply script ${buildScriptA.displayName} to project :lib finished", 100, 900))
    analyzer.receiveEvent(createProjectConfigurationFinishEventStub(":lib", 0, 1000))

    analyzer.onBuildSuccess()
  }

  private fun checkConfigurationData(isPluginCSuppressed: Boolean) {
    assertThat(analyzer.projectsConfigurationData).hasSize(2)

    assertThat(analyzer.projectsConfigurationData[0].project).isEqualTo(":app")

    var pluginAData = PluginConfigurationData(PluginData(pluginA, ""), Duration.ofMillis(500), emptyList(), true)
    var agpData = PluginConfigurationData(PluginData(applicationPlugin, ""), Duration.ofMillis(250), emptyList())
    var pluginBData = PluginConfigurationData(PluginData(pluginB, ""), Duration.ofMillis(100), emptyList())
    var pluginCData = PluginConfigurationData(PluginData(pluginC, ""), Duration.ofMillis(400), emptyList(), !isPluginCSuppressed)
    var buildScriptBData = PluginConfigurationData(PluginData(buildScriptB, ":app"), Duration.ofMillis(450),
                                                   listOf(pluginCData))
    var buildScriptAData = PluginConfigurationData(PluginData(buildScriptA, ":app"), Duration.ofMillis(1000),
                                                   listOf(agpData, pluginBData, buildScriptBData))

    assertThat(analyzer.projectsConfigurationData[0].pluginsConfigurationData).containsExactlyElementsIn(
      listOf(pluginAData, buildScriptAData))

    assertThat(analyzer.projectsConfigurationData[1].project).isEqualTo(":lib")

    pluginAData = PluginConfigurationData(PluginData(pluginA, ""), Duration.ofMillis(100), emptyList())
    agpData = PluginConfigurationData(PluginData(libraryPlugin, ""), Duration.ofMillis(150), emptyList())
    pluginBData = PluginConfigurationData(PluginData(pluginB, ""), Duration.ofMillis(250), emptyList(), true)
    pluginCData = PluginConfigurationData(PluginData(pluginC, ""), Duration.ofMillis(100), emptyList())
    buildScriptBData = PluginConfigurationData(PluginData(buildScriptB, ":lib"), Duration.ofMillis(200),
                                               listOf(pluginCData))
    buildScriptAData = PluginConfigurationData(PluginData(buildScriptA, ":lib"), Duration.ofMillis(800),
                                               listOf(agpData, pluginBData, buildScriptBData))

    assertThat(analyzer.projectsConfigurationData[1].pluginsConfigurationData).containsExactlyElementsIn(
      listOf(pluginAData, buildScriptAData))
  }

  @Test
  fun testProjectConfigurationAnalyzer() {
    sendProjectConfigurationEventsToAnalyzer()

    checkConfigurationData(false)
  }

  @Test
  fun testProjectConfigurationAnalyzerWithSuppressedWarnings() {
    warningsFilter.suppressPluginSlowingConfigurationWarning(pluginC.displayName)

    sendProjectConfigurationEventsToAnalyzer()

    checkConfigurationData(true)
  }
}
