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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.data.AnnotationProcessorData
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class AnnotationProcessorsReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testTasksCriticalPath() {
    val analyzerResults = object : MockResultsProvider() {
      override fun getBuildFinishedTimestamp(): Long = 12345
      override fun getAnnotationProcessorsData(): List<AnnotationProcessorData> = listOf(
        AnnotationProcessorData("com.google.auto.value.processor.AutoAnnotationProcessor", Duration.ofMillis(123)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueBuilderProcessor", Duration.ofMillis(456)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoOneOfProcessor", Duration.ofMillis(789)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueProcessor", Duration.ofMillis(101)),
        AnnotationProcessorData("com.google.auto.value.extension.memoized.processor.MemoizedValidator", Duration.ofMillis(102)),
        AnnotationProcessorData("dagger.internal.codegen.ComponentProcessor", Duration.ofMillis(103))
      )

      override fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData> = listOf(
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueBuilderProcessor", Duration.ofMillis(456)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueProcessor", Duration.ofMillis(101)),
        AnnotationProcessorData("dagger.internal.codegen.ComponentProcessor", Duration.ofMillis(103))
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults).build()

    assertThat(report.annotationProcessors.nonIncrementalProcessors.size).isEqualTo(3)
    assertThat(report.annotationProcessors.nonIncrementalProcessors[0].className).isEqualTo(
      "com.google.auto.value.processor.AutoValueBuilderProcessor")
    assertThat(report.annotationProcessors.nonIncrementalProcessors[0].compilationTimeMs).isEqualTo(456)
    assertThat(report.annotationProcessors.nonIncrementalProcessors[1].className).isEqualTo(
      "dagger.internal.codegen.ComponentProcessor")
    assertThat(report.annotationProcessors.nonIncrementalProcessors[1].compilationTimeMs).isEqualTo(103)
    assertThat(report.annotationProcessors.nonIncrementalProcessors[2].className).isEqualTo(
      "com.google.auto.value.processor.AutoValueProcessor")
    assertThat(report.annotationProcessors.nonIncrementalProcessors[2].compilationTimeMs).isEqualTo(101)
  }
}