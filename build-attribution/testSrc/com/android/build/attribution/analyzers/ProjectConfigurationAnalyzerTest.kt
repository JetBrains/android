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

import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.PluginIdentifier
import org.junit.Test

class ProjectConfigurationAnalyzerTest {

  private val analyzer = ProjectConfigurationAnalyzer(PluginContainer())

  private val pluginA = createBinaryPluginIdentifierStub("pluginA")
  private val pluginB = createBinaryPluginIdentifierStub("pluginB")
  private val pluginC = createBinaryPluginIdentifierStub("pluginC")
  private val pluginD = createBinaryPluginIdentifierStub("pluginD")
  private val pluginE = createBinaryPluginIdentifierStub("pluginE")
  private val buildScriptA = createScriptPluginIdentifierStub("buildA.gradle")
  private val buildScriptB = createScriptPluginIdentifierStub("buildB.gradle")

  private fun createApplyPluginFinishEvent(plugin: PluginIdentifier,
                                           configurationTime: Long,
                                           parentEvent: FinishEvent? = null): FinishEvent {
    return createFinishEventStub(
      "Apply ${if (plugin is BinaryPluginIdentifier) "plugin" else "script"} ${plugin.displayName} to project :app finished",
      0,
      configurationTime,
      createOperationDescriptorStub("Apply ${if (plugin is BinaryPluginIdentifier) "plugin" else "script"} ${plugin.displayName}",
                                    parent = parentEvent?.descriptor))
  }

  private fun sendProjectConfigurationEventsToAnalyzer() {
    analyzer.onBuildStart()

    // The following events represent this tree
    //
    // plugin pluginA (0.5 sec)
    //
    // Compile script buildA.gradle (BODY) (0.5 sec)
    // Compile script buildA.gradle (CLASSPATH) (0.5 sec)
    //
    // script :app:buildA.gradle (2.3 sec) {
    //   Resolve dependencies of :classpath (0.7 sec)
    //   Resolve files of :classpath (0.3 sec)
    //   plugin pluginB (0.1 sec)
    //   script :app:buildB.gradle (0.5 sec) {
    //     Resolve files of :classpath (0.1 sec)
    //     plugin pluginC (0.4 sec) {
    //       plugin pluginD (0.2 sec)
    //     }
    //   }
    //
    //   allProjects (0.5 sec)
    // }
    //
    // afterEvaluate (0.7 sec) {
    //   apply pluginE (0.2 sec)
    // }

    analyzer.receiveEvent(createProjectConfigurationStartEventStub(":app"))

    analyzer.receiveEvent(createApplyPluginFinishEvent(pluginA, 500))

    analyzer.receiveEvent(createFinishEventStub("Compile script buildA.gradle (BODY) finished", 0, 500,
                                                createOperationDescriptorStub("Compile script buildA.gradle (BODY)")))

    analyzer.receiveEvent(createFinishEventStub("Compile script buildA.gradle (CLASSPATH) finished", 0, 500,
                                                createOperationDescriptorStub("Compile script buildA.gradle (CLASSPATH)")))

    val buildScriptAFinishEvent = createApplyPluginFinishEvent(buildScriptA, 3000)

    analyzer.receiveEvent(createFinishEventStub("Resolve dependencies of :classpath finished", 0, 700,
                                                createOperationDescriptorStub("Resolve dependencies of :classpath",
                                                                              parent = buildScriptAFinishEvent.descriptor)))

    analyzer.receiveEvent(createFinishEventStub("Resolve files of :classpath finished", 0, 300,
                                                createOperationDescriptorStub("Resolve files of :classpath",
                                                                              parent = buildScriptAFinishEvent.descriptor)))

    analyzer.receiveEvent(createApplyPluginFinishEvent(pluginB, 100))

    val buildScriptBFinishEvent = createApplyPluginFinishEvent(buildScriptB, 500, buildScriptAFinishEvent)

    analyzer.receiveEvent(createFinishEventStub("Resolve files of :classpath finished", 0, 100,
                                                createOperationDescriptorStub("Resolve files of :classpath",
                                                                              parent = buildScriptBFinishEvent.descriptor)))

    val pluginCConfigurationFinishEvent = createApplyPluginFinishEvent(pluginC, 400, buildScriptBFinishEvent)

    analyzer.receiveEvent(createApplyPluginFinishEvent(pluginD, 200, pluginCConfigurationFinishEvent))
    analyzer.receiveEvent(pluginCConfigurationFinishEvent)
    analyzer.receiveEvent(buildScriptBFinishEvent)

    analyzer.receiveEvent(createFinishEventStub("Execute 'allProjects {}' action finished", 0, 500,
                                                createOperationDescriptorStub("allProjects",
                                                                              "Execute 'allProjects {}' action",
                                                                              buildScriptAFinishEvent.descriptor)))

    analyzer.receiveEvent(buildScriptAFinishEvent)

    val afterEvaluateFinishEvent = createFinishEventStub("Notify afterEvaluate listeners of :app finished", 0, 700,
                                                         createOperationDescriptorStub("Notify afterEvaluate listeners of :app"))

    analyzer.receiveEvent(createApplyPluginFinishEvent(pluginE, 200, afterEvaluateFinishEvent))
    analyzer.receiveEvent(afterEvaluateFinishEvent)

    analyzer.receiveEvent(createProjectConfigurationFinishEventStub(":app", 0, 4500))

  }

  @Test
  fun testProjectConfigurationAnalyzer() {
    sendProjectConfigurationEventsToAnalyzer()

    assertThat(analyzer.result.projectsConfigurationData).hasSize(1)

    val configurationData = analyzer.result.projectsConfigurationData[0]

    assertThat(configurationData.projectPath).isEqualTo(":app")

    val expectedPluginsConfiguration = listOf(PluginConfigurationData(PluginData(pluginA, ""), 500),
                                              PluginConfigurationData(PluginData(pluginB, ""), 100),
                                              PluginConfigurationData(PluginData(pluginC, ""), 400),
                                              PluginConfigurationData(PluginData(pluginE, ""), 200))

    assertThat(
      analyzer.result.pluginsConfigurationDataMap.map { (plugin, time) ->
        PluginConfigurationData(plugin, time)
      }).containsExactlyElementsIn(
      expectedPluginsConfiguration)

    assertThat(configurationData.pluginsConfigurationData).containsExactlyElementsIn(expectedPluginsConfiguration)

    assertThat(configurationData.totalConfigurationTimeMs).isEqualTo(4500)

    assertThat(configurationData.configurationSteps).hasSize(5)

    assertThat(configurationData.configurationSteps[0].type).isEquivalentAccordingToCompareTo(
      ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS)
    assertThat(configurationData.configurationSteps[0].configurationTimeMs).isEqualTo(500)

    assertThat(configurationData.configurationSteps[1].type).isEquivalentAccordingToCompareTo(
      ProjectConfigurationData.ConfigurationStep.Type.RESOLVING_DEPENDENCIES)
    assertThat(configurationData.configurationSteps[1].configurationTimeMs).isEqualTo(1100)

    assertThat(configurationData.configurationSteps[2].type).isEquivalentAccordingToCompareTo(
      ProjectConfigurationData.ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS)
    assertThat(configurationData.configurationSteps[2].configurationTimeMs).isEqualTo(1000)

    assertThat(configurationData.configurationSteps[3].type).isEquivalentAccordingToCompareTo(
      ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS)
    assertThat(configurationData.configurationSteps[3].configurationTimeMs).isEqualTo(500)

    assertThat(configurationData.configurationSteps[4].type).isEquivalentAccordingToCompareTo(
      ProjectConfigurationData.ConfigurationStep.Type.OTHER)
    assertThat(configurationData.configurationSteps[4].configurationTimeMs).isEqualTo(200)
  }
}
