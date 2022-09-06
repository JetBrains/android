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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigurationTimeReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testConfigurationTimesReport() {

    val analyzerResults = object : MockResultsProvider() {
      override fun getBuildFinishedTimestamp(): Long = 12345

      override fun getConfigurationPhaseTimeMs(): Long = 2000

      override fun getProjectsConfigurationData(): List<ProjectConfigurationData> = listOf(
        project(":app", 1000, listOf(
          plugin(pluginA, 200),
          plugin(pluginB, 100),
          plugin(pluginC, 700)
        )),
        project(":lib", 500, listOf(
          plugin(pluginA, 200),
          plugin(libraryPlugin, 300)
        ))
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults).build()

    assertThat(report.configurationTime.totalConfigurationTime.timeMs).isEqualTo(2000)
    assertThat(report.configurationTime.projects.size).isEqualTo(2)
    assertThat(report.configurationTime.projects[0].configurationTime).isEqualTo(TimeWithPercentage(1000, 2000))
    assertThat(report.configurationTime.projects[0].project).isEqualTo(":app")
    assertThat(report.configurationTime.projects[1].configurationTime).isEqualTo(TimeWithPercentage(500, 2000))
    assertThat(report.configurationTime.projects[1].project).isEqualTo(":lib")

    assertThat(report.configurationTime.projects[0].plugins.size).isEqualTo(3)
    assertThat(report.configurationTime.projects[0].plugins[0].pluginName).isEqualTo("pluginC")
    assertThat(report.configurationTime.projects[0].plugins[0].configurationTime).isEqualTo(TimeWithPercentage(700, 2000))
    assertThat(report.configurationTime.projects[0].plugins[1].pluginName).isEqualTo("pluginA")
    assertThat(report.configurationTime.projects[0].plugins[1].configurationTime).isEqualTo(TimeWithPercentage(200, 2000))
    assertThat(report.configurationTime.projects[0].plugins[2].pluginName).isEqualTo("pluginB")
    assertThat(report.configurationTime.projects[0].plugins[2].configurationTime).isEqualTo(TimeWithPercentage(100, 2000))

    assertThat(report.configurationTime.projects[1].plugins.size).isEqualTo(2)
    assertThat(report.configurationTime.projects[1].plugins[0].pluginName).isEqualTo("com.android.library")
    assertThat(report.configurationTime.projects[1].plugins[0].configurationTime).isEqualTo(TimeWithPercentage(300, 2000))
    assertThat(report.configurationTime.projects[1].plugins[1].pluginName).isEqualTo("pluginA")
    assertThat(report.configurationTime.projects[1].plugins[1].configurationTime).isEqualTo(TimeWithPercentage(200, 2000))
  }
}