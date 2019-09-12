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
package com.android.build.attribution.analyzers

import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.tooling.events.ProgressEvent

class BuildEventsAnalyzersWrapper(private val buildEventsAnalyzers: List<BuildEventsAnalyzer>,
                                  private val buildAttributionReportAnalyzers: List<BuildAttributionReportAnalyzer>) {
  fun onBuildStart() {
    buildEventsAnalyzers.forEach(BuildEventsAnalyzer::onBuildStart)
    buildAttributionReportAnalyzers.forEach(BuildAttributionReportAnalyzer::onBuildStart)
  }

  fun onBuildSuccess(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData?) {
    buildEventsAnalyzers.forEach(BuildEventsAnalyzer::onBuildSuccess)

    if (androidGradlePluginAttributionData != null) {
      buildAttributionReportAnalyzers.forEach { it.receiveBuildAttributionReport(androidGradlePluginAttributionData) }
    }
  }

  fun onBuildFailure() {
    buildEventsAnalyzers.forEach(BuildEventsAnalyzer::onBuildFailure)
  }

  fun receiveEvent(event: ProgressEvent) {
    buildEventsAnalyzers.forEach { it.receiveEvent(event) }
  }
}