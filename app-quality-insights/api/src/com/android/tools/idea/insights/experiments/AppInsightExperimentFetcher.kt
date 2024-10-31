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

import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.AqiExperimentsConfig
import com.android.tools.idea.serverflags.protos.ExperimentType
import com.intellij.openapi.components.service

interface AppInsightsExperimentFetcher {
  fun getCurrentExperiment(experimentGroup: ExperimentGroup): Experiment

  companion object {
    val instance: AppInsightsExperimentFetcher
      get() = service<AppInsightsExperimentFetcher>()
  }
}

class AppInsightExperimentFetcherImpl : AppInsightsExperimentFetcher {

  override fun getCurrentExperiment(experimentGroup: ExperimentGroup): Experiment {
    experimentGroup.experiments.forEach { experiment ->
      if (isFlagExpected("experiments/aqi/${experiment.flagName}", experiment.protoType))
        return experiment
    }
    return Experiment.UNKNOWN
  }

  private fun isFlagExpected(flag: String, expected: ExperimentType) =
    ServerFlagService.instance
      .getProto(flag, AqiExperimentsConfig.getDefaultInstance())
      .experimentType == expected
}

private val CONTEXT_SHARING_EXPERIMENTS =
  setOf(Experiment.TOP_SOURCE, Experiment.TOP_THREE_SOURCES, Experiment.ALL_SOURCES)

fun Experiment.supportsContextSharing() = this in CONTEXT_SHARING_EXPERIMENTS
