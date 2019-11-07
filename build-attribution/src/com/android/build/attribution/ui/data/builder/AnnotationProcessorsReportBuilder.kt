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

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport


class AnnotationProcessorsReportBuilder(
  val analyzersResultsProvider: BuildEventsAnalysisResult
) {

  fun build(): AnnotationProcessorsReport = object : AnnotationProcessorsReport {
    override val nonIncrementalProcessors: List<AnnotationProcessorUiData> = analyzersResultsProvider
      .getNonIncrementalAnnotationProcessorsData()
      .map {
        object : AnnotationProcessorUiData {
          override val className = it.className
          override val compilationTimeMs = it.compilationDuration.toMillis()
        }
      }
      .sortedByDescending { it.compilationTimeMs }
  }
}
