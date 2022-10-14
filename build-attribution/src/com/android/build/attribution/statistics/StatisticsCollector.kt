/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.statistics

import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.intellij.openapi.project.Project
import java.util.function.Consumer

abstract class SingleStatisticsCollector : Consumer<BuildAnalysisResults>

class StatisticsCollector(private val project: Project) {

  /**
   * Call [singleCollector] on each build result, in increasing build finished time order
   */
  fun collectStatistics(singleCollector: SingleStatisticsCollector): SingleStatisticsCollector {
    BuildAnalyzerStorageManager.getInstance(project).getListOfHistoricBuildDescriptors().sortedBy { it.buildFinishedTimestamp }.forEach { resultDescriptor ->
      var result: BuildAnalysisResults? = null
      try {
        result = BuildAnalyzerStorageManager.getInstance(project).getHistoricBuildResultByID(resultDescriptor.buildSessionID).get()
      }
      catch (_: NoSuchElementException) {
      }
      result?.let {
        singleCollector.accept(it)
      }
    }
    return singleCollector
  }
}