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
import com.android.tools.idea.flags.StudioFlags
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
  private var isCheckJetifierBuild: Boolean = false
  private var lastCheckJetifierBuildTimestamp: Long? = null


  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalysisResult, studioProvidedInfo: StudioProvidedInfo) {
    if (!StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.get()) return
    enableJetifierFlagState = studioProvidedInfo.enableJetifierPropertyState
    useAndroidXFlagState = studioProvidedInfo.useAndroidXPropertyState

    checkJetifierResultFile(studioProvidedInfo.buildRequestHolder.buildRequest).let {
      if (it.exists()) {
        checkJetifierResult = CheckJetifierResult.load(it)
        lastCheckJetifierBuildTimestamp = System.currentTimeMillis()
        isCheckJetifierBuild = true
      }
    }
  }

  override fun calculateResult(): JetifierUsageAnalyzerResult {
    if (!StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.get()) return JetifierUsageAnalyzerResult(AnalyzerNotRun, lastCheckJetifierBuildTimestamp, false)
    if (enableJetifierFlagState == true && useAndroidXFlagState == true) {
      return checkJetifierResult?.let {
        if (it.isEmpty()) JetifierUsageAnalyzerResult(JetifierCanBeRemoved, lastCheckJetifierBuildTimestamp, isCheckJetifierBuild)
        else JetifierUsageAnalyzerResult(JetifierRequiredForLibraries(it), lastCheckJetifierBuildTimestamp, isCheckJetifierBuild)
      } ?: JetifierUsageAnalyzerResult(JetifierUsedCheckRequired, lastCheckJetifierBuildTimestamp, false)
    }
    return JetifierUsageAnalyzerResult(JetifierNotUsed, lastCheckJetifierBuildTimestamp, false)
  }

  override fun cleanupTempState() {
    // Leave checkJetifierResult and lastCheckJetifierBuildTimestamp for future reports to not load it on every build.
    enableJetifierFlagState = null
    useAndroidXFlagState = null
    isCheckJetifierBuild = false
  }
}

data class JetifierUsageAnalyzerResult(
  val projectStatus: JetifierUsageProjectStatus,
  val lastCheckJetifierBuildTimestamp: Long? = null,
  /** If current build was a checkJetifier task request. */
  val checkJetifierBuild: Boolean = false
) : AnalyzerResult

sealed class JetifierUsageProjectStatus

object AnalyzerNotRun : JetifierUsageProjectStatus()
object JetifierNotUsed : JetifierUsageProjectStatus()
object JetifierUsedCheckRequired : JetifierUsageProjectStatus()
object JetifierCanBeRemoved : JetifierUsageProjectStatus()
data class JetifierRequiredForLibraries(val checkJetifierResult: CheckJetifierResult) : JetifierUsageProjectStatus()
