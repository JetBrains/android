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

import com.android.tools.idea.serverflags.protos.ExperimentType

/** Declares all experiments conducted in AQI. */
enum class Experiment(val flagName: String, val protoType: ExperimentType) {
  UNKNOWN("", ExperimentType.EXPERIMENT_TYPE_UNSPECIFIED),

  // Code context experiments.
  CONTROL("aqi.code.context.experiment.1", ExperimentType.CONTROL),
  TOP_SOURCE("aqi.code.context.experiment.2", ExperimentType.TOP_SOURCE),
  TOP_THREE_SOURCES("aqi.code.context.experiment.3", ExperimentType.TOP_THREE_SOURCES),
  ALL_SOURCES("aqi.code.context.experiment.4", ExperimentType.ALL_SOURCES),
}
