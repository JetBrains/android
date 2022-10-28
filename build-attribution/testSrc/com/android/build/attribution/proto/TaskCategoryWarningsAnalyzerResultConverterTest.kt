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
package com.android.build.attribution.proto

import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.proto.converters.TaskCategoryWarningsAnalyzerResultConverter
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.google.common.truth.Truth
import org.junit.Test

class TaskCategoryWarningsAnalyzerResultConverterTest {
  @Test
  fun testTaskCategoryWarningsAnalyzerResult() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.IssuesResult(
      listOf(
        TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
        TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR,
        TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD
      )
    )
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(taskCategoryWarningsAnalyzerResult)
  }

  @Test
  fun testNotEqualsTaskCategoryWarningsAnalyzerResult() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.IssuesResult(
      listOf(
        TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
        TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR,
        TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD
      )
    )
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    val anotherTaskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.IssuesResult(
      listOf(
        TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
        TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR,
      )
    )
    Truth.assertThat(resultConverted).isNotEqualTo(anotherTaskCategoryWarningsAnalyzerResult)
  }

  @Test
  fun testTaskCategoryWarningsAnalyzerResultNoDataFromAGP() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.NoDataFromAGP
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(taskCategoryWarningsAnalyzerResult)
  }

  @Test
  fun testTaskCategoryWarningsAnalyzerResultFeatureDisabled() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.FeatureDisabled
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(taskCategoryWarningsAnalyzerResult)
  }
}