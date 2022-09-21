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
import com.android.build.attribution.proto.converters.BuildResultsProtoMessageConverter
import com.android.build.attribution.proto.converters.TaskCategoryWarningsAnalyzerResultConverter
import com.android.ide.common.attribution.BuildAnalyzerTaskCategoryIssue
import com.google.common.truth.Truth
import org.junit.Test

class TaskCategoryWarningsAnalyzerResultConverterTest {
  @Test
  fun testTaskCategoryWarningsAnalyzerResult() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.Result(
      listOf(BuildAnalyzerTaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
             BuildAnalyzerTaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR,
             BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED,
             BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED)
    )
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(taskCategoryWarningsAnalyzerResult)
  }

  @Test
  fun testNotEqualsTaskCategoryWarningsAnalyzerResult() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.Result(
      listOf(BuildAnalyzerTaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
             BuildAnalyzerTaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR,
             BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED,
             BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED)
    )
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    val anotherTaskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.Result(
      listOf(BuildAnalyzerTaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED,
             BuildAnalyzerTaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR,
             BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED)
    )
    Truth.assertThat(resultConverted).isNotEqualTo(anotherTaskCategoryWarningsAnalyzerResult)
  }

  @Test
  fun testTaskCategoryWarningsAnalyzerResultEmpty() {
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.Result(emptyList())
    val resultMessage = TaskCategoryWarningsAnalyzerResultConverter.transform(
      taskCategoryWarningsAnalyzerResult)
    val resultConverted = TaskCategoryWarningsAnalyzerResultConverter.construct(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(taskCategoryWarningsAnalyzerResult)
  }
}