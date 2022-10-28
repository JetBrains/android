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

import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.getSuccessfulResult
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskCategoryWarningsAnalyzerTest {
  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Before
  fun setUp() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.clearOverride()
  }

  @Test
  fun testExpectedWarnings() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    preparedProject.runTest {
      invokeTasks("preBuild")
      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()
      assertThat(results.getTaskCategoryWarningsAnalyzerResult()).isInstanceOf(TaskCategoryWarningsAnalyzer.IssuesResult::class.java)
      assertThat(
        (results.getTaskCategoryWarningsAnalyzerResult() as TaskCategoryWarningsAnalyzer.IssuesResult).taskCategoryIssues
      ).containsExactly(
        TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
        TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED,
        TaskCategoryIssue.RESOURCE_VALIDATION_ENABLED,
        TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD
      )
    }
  }
}