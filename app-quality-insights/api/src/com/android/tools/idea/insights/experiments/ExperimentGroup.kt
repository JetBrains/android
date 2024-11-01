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

/**
 * Identifies a group of related experiments.
 *
 * [description] is for display purposes only.
 *
 * [experiments] indicates the experiments that belong to this group. Note the order of the
 * experiment entries is important for the proper assignment of experiments according to the server
 * flag definitions.
 */
enum class ExperimentGroup(val description: String, val experiments: List<Experiment>) {
  UNKNOWN_GROUP("unknown experiment", emptyList()),
  CODE_CONTEXT(
    "Code Context Experiments",
    listOf(
      Experiment.CONTROL,
      Experiment.TOP_SOURCE,
      Experiment.TOP_THREE_SOURCES,
      Experiment.ALL_SOURCES,
    ),
  ),
}
