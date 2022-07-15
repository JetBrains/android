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
package com.android.build.attribution

import com.android.tools.idea.gradle.project.build.attribution.BasicBuildAttributionInfo
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import org.gradle.tooling.events.ProgressListener
import com.android.build.attribution.analyzers.BuildAnalyzersWrapper
import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.analyzers.CHECK_JETIFIER_TASK_NAME
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.ide.common.repository.GradleVersion
import com.intellij.openapi.project.Project
interface BuildAnalyzerStorageManager
{
  fun getLatestBuildAnalysisResults() : BuildAnalysisResults
  fun storeNewBuildResults(analyzersProxy: BuildEventsAnalyzersProxy, buildID: String, requestHolder: BuildRequestHolder)
  fun hasData() : Boolean

  companion object {
    fun getInstance(project: Project): BuildAnalyzerStorageManager {
      return project.getService(BuildAnalyzerStorageManager::class.java)
    }
  }
}