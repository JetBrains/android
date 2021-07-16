/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.utils.FileUtils
import java.io.File

fun checkJetifierResultFile(buildRequest: GradleBuildInvoker.Request): File = FileUtils.join(
  getAgpAttributionFileDir(buildRequest),
  SdkConstants.FD_BUILD_ATTRIBUTION,
  "checkJetifierResult.json"
)

class JetifierUsageAnalyzer : BaseAnalyzer<JetifierUsageAnalyzerResult>(), PostBuildProcessAnalyzer {
  private var enableJetifierFlagState: Boolean? = null
  private var useAndroidXFlagState: Boolean? = null
  private var checkJetifierResult: CheckJetifierResult? = null

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalysisResult, studioProvidedInfo: StudioProvidedInfo) {
    enableJetifierFlagState = studioProvidedInfo.enableJetifierPropertyState
    useAndroidXFlagState = studioProvidedInfo.useAndroidXPropertyState

    checkJetifierResultFile(studioProvidedInfo.buildRequestHolder.buildRequest).let {
      if (it.exists()) {
        checkJetifierResult = CheckJetifierResult.load(it)
      }
    }
    // TODO (b/194299215): need to copy to IJ data folder, load from there if  missing here, delete from data folder if jetifier is off.
  }

  override fun calculateResult(): JetifierUsageAnalyzerResult {
    if (enableJetifierFlagState == true && useAndroidXFlagState == true) {
      return checkJetifierResult?.let {
        if (it.isEmpty()) JetifierCanBeRemoved
        else JetifierRequiredForLibraries(it)
      } ?: JetifierUsedCheckRequired
    }
    return JetifierNotUsed
  }

  override fun cleanupTempState() {
    // Leave checkJetifierResult for future reports to not load it on every build.
    enableJetifierFlagState = null
    useAndroidXFlagState = null
  }
}

sealed class JetifierUsageAnalyzerResult : AnalyzerResult

object JetifierNotUsed : JetifierUsageAnalyzerResult()
object JetifierUsedCheckRequired : JetifierUsageAnalyzerResult()
object JetifierCanBeRemoved : JetifierUsageAnalyzerResult()
data class JetifierRequiredForLibraries(val checkJetifierResult: CheckJetifierResult) : JetifierUsageAnalyzerResult()
