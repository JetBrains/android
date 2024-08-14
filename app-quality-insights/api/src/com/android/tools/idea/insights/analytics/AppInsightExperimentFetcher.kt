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
package com.android.tools.idea.insights.analytics

import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.AqiExperimentsConfig
import com.android.tools.idea.serverflags.protos.ExperimentType

private const val EXPERIMENT_CONTROL = "experiments/aqi/aqi.code.context.experiment.1"
private const val EXPERIMENT_TOP_SOURCE = "experiments/aqi/aqi.code.context.experiment.2"
private const val EXPERIMENT_TOP_THREE_SOURCES = "experiments/aqi/aqi.code.context.experiment.3"
private const val EXPERIMENT_ALL_SOURCES = "experiments/aqi/aqi.code.context.experiment.4"

private val experimentToExpectedMap =
  mapOf(
    EXPERIMENT_CONTROL to ExperimentType.CONTROL,
    EXPERIMENT_TOP_SOURCE to ExperimentType.TOP_SOURCE,
    EXPERIMENT_TOP_THREE_SOURCES to ExperimentType.TOP_THREE_SOURCES,
    EXPERIMENT_ALL_SOURCES to ExperimentType.ALL_SOURCES,
  )

object AppInsightExperimentFetcher {

  fun getCurrentExperiment(): ExperimentType {
    experimentToExpectedMap.entries.forEach { (flag, expected) ->
      if (isFlagExpected(flag, expected)) return expected
    }
    return ExperimentType.EXPERIMENT_TYPE_UNSPECIFIED
  }

  private fun isFlagExpected(flag: String, expected: ExperimentType) =
    ServerFlagService.instance
      .getProto(flag, AqiExperimentsConfig.getDefaultInstance())
      .experimentType == expected
}
