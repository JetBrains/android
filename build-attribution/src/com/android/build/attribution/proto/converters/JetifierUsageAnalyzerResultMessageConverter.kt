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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsageProjectStatus
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired

class JetifierUsageAnalyzerResultMessageConverter {
  companion object {
    fun transform(jetifierUsageAnalyzerResult: JetifierUsageAnalyzerResult)
      : BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult {
      val jetifierUsageAnalyzerResultBuilder = BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.newBuilder()
        .setProjectStatus(transformProjectStatus(jetifierUsageAnalyzerResult.projectStatus))
        .setCheckJetifierBuild(jetifierUsageAnalyzerResult.checkJetifierBuild)
      if (jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp != null) {
        jetifierUsageAnalyzerResultBuilder.lastCheckJetifierBuildTimestamp = jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp
      }
      return jetifierUsageAnalyzerResultBuilder.build()
    }

    fun construct(jetifierUsageAnalyzerResult: BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult)
      : JetifierUsageAnalyzerResult {
      val lastCheckJetifierBuildTimestamp = when (jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp) {
        0L -> null
        else -> jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp
      }
      return JetifierUsageAnalyzerResult(AnalyzerNotRun, lastCheckJetifierBuildTimestamp, jetifierUsageAnalyzerResult.checkJetifierBuild)
    }

    private fun transformProjectStatus(jetifierUsageProjectStatus: JetifierUsageProjectStatus) =
      when (jetifierUsageProjectStatus) {
        AnalyzerNotRun -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.ANALYZER_NOT_RUN
        JetifierNotUsed -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.JETIFIER_NOT_USED
        JetifierUsedCheckRequired -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.JETIFIER_USED_CHECK_REQUIRED
        JetifierCanBeRemoved -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.JETIFIER_CAN_BE_REMOVED
        is JetifierRequiredForLibraries -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.IS_JETIFIER_REQUIRED_FOR_LIBRARIES
      }
  }
}