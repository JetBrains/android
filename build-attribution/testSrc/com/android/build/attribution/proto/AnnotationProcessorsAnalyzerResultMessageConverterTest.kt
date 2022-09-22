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

import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.data.AnnotationProcessorData
import com.google.common.truth.Truth
import org.junit.Test
import java.time.Duration

class AnnotationProcessorsAnalyzerResultMessageConverterTest {
  @Test
  fun testAnnotationProcessorsAnalyzerResult() {
    val annotationProcessorData = listOf(
      AnnotationProcessorData("com.google.auto.value.processor.AutoAnnotationProcessor", Duration.ofMillis(123)),
      AnnotationProcessorData("com.google.auto.value.processor.AutoValueBuilderProcessor", Duration.ofMillis(456)),
      AnnotationProcessorData("com.google.auto.value.processor.AutoOneOfProcessor", Duration.ofMillis(789)),
      AnnotationProcessorData("com.google.auto.value.processor.AutoValueProcessor", Duration.ofMillis(101)),
      AnnotationProcessorData("com.google.auto.value.extension.memoized.processor.MemoizedValidator", Duration.ofMillis(102)),
      AnnotationProcessorData("dagger.internal.codegen.ComponentProcessor", Duration.ofMillis(103))
    )
    val nonIncrementalAnnotationProcessorData = listOf(
      AnnotationProcessorData("com.google.auto.value.processor.AutoValueBuilderProcessor", Duration.ofMillis(456)),
      AnnotationProcessorData("com.google.auto.value.processor.AutoValueProcessor", Duration.ofMillis(101)),
      AnnotationProcessorData("dagger.internal.codegen.ComponentProcessor", Duration.ofMillis(103))
    )
    val annotationProcessorsAnalyzerResult = AnnotationProcessorsAnalyzer.Result(annotationProcessorData,
                                                                                 nonIncrementalAnnotationProcessorData)
    val annotationProcessorsAnalyzerMessageResult = AnnotationProcessorsAnalyzerResultMessageConverter.transform(
      annotationProcessorsAnalyzerResult)
    val annotationProcessorAnalyzerResultConverted = AnnotationProcessorsAnalyzerResultMessageConverter.construct(
      annotationProcessorsAnalyzerMessageResult)
    Truth.assertThat(annotationProcessorAnalyzerResultConverted).isEqualTo(annotationProcessorsAnalyzerResult)
  }
}