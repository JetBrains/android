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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.ide.common.attribution.TaskCategory
import com.android.ide.common.attribution.BuildAnalyzerTaskCategoryIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskCategoryWarningsAnalyzerTest {

  private val data = AndroidGradlePluginAttributionData(
    buildAnalyzerTaskCategoryIssues = listOf(BuildAnalyzerTaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED)
  )

  @Test
  fun resultsFromAGPDataIsCorrect() {
    val taskContainer = TaskContainer()
    val analyzer = TaskCategoryWarningsAnalyzer(taskContainer)
    analyzer.receiveBuildAttributionReport(data)
    analyzer.ensureResultCalculated()
    val taskCategoryWarningResult = listOf(BuildAnalyzerTaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED)
    assertThat(analyzer.result.buildAnalyzerTaskCategoryIssues).containsExactlyElementsIn(taskCategoryWarningResult)
  }
}