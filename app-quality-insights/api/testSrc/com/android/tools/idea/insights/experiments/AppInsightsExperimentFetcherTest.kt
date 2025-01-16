/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.experiments

import com.android.mockito.kotlin.whenever
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.AqiExperimentsConfig
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppInsightsExperimentFetcherTest {
  @get:Rule val projectRule = ProjectRule()

  private lateinit var activeExperiment: Experiment
  private lateinit var serverFlagService: ServerFlagService

  private val experimentFetcher: AppInsightsExperimentFetcher
    get() = AppInsightsExperimentFetcher.instance

  @Before
  fun setUp() {
    activeExperiment = Experiment.UNKNOWN
    serverFlagService =
      object : ServerFlagService {
        override val configurationVersion = 0L
        override val flagAssignments = emptyMap<String, Int>()

        override fun <T : Message> getProtoOrNull(name: String, instance: T): T? {
          if (activeExperiment == Experiment.UNKNOWN) return null
          @Suppress("UNCHECKED_CAST")
          return AqiExperimentsConfig.newBuilder()
            .apply { experimentType = activeExperiment.protoType }
            .build() as T?
        }
      }
    ApplicationManager.getApplication()
      .replaceService(ServerFlagService::class.java, serverFlagService, projectRule.disposable)
  }

  @Test
  fun `fetch code context experiments`() {
    activeExperiment = Experiment.UNKNOWN
    assertThat(experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT))
      .isEqualTo(Experiment.UNKNOWN)

    activeExperiment = Experiment.ALL_SOURCES
    assertThat(experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT))
      .isEqualTo(Experiment.ALL_SOURCES)

    activeExperiment = Experiment.TOP_THREE_SOURCES
    assertThat(experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT))
      .isEqualTo(Experiment.TOP_THREE_SOURCES)

    activeExperiment = Experiment.TOP_SOURCE
    assertThat(experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT))
      .isEqualTo(Experiment.TOP_SOURCE)

    activeExperiment = Experiment.CONTROL
    assertThat(experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT))
      .isEqualTo(Experiment.CONTROL)
  }

  @Test
  fun `test logs experiment once`() {
    val mockLogger = mockStatic<Logger>(projectRule.disposable)
    val logs = mutableListOf<String?>()
    val fakeLogger =
      object : Logger() {
        override fun isDebugEnabled() = false

        override fun debug(message: String?, t: Throwable?) = Unit

        override fun info(message: String?, t: Throwable?) {
          logs.add(message)
        }

        override fun warn(message: String?, t: Throwable?) = Unit

        override fun error(message: String?, t: Throwable?, vararg details: String?) = Unit
      }

    mockLogger
      .whenever<Any> { Logger.getInstance(AppInsightExperimentFetcherImpl::class.java) }
      .thenReturn(fakeLogger)

    activeExperiment = Experiment.TOP_THREE_SOURCES
    repeat(10) { experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT) }

    assertThat(logs.size).isEqualTo(1)
    assertThat(logs[0]).isEqualTo("Assigned to AQI experiment: TOP_THREE_SOURCES")
  }
}
