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

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.BuildAnalysisResultsMessage.AnnotationProcessorsAnalyzerResult
import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.data.AnnotationProcessorData
import java.time.Duration

class AnnotationProcessorsAnalyzerResultMessageConverter {
  companion object {
    fun transform(annotationProcessorsAnalyzerResult: AnnotationProcessorsAnalyzer.Result)
      : AnnotationProcessorsAnalyzerResult =
      AnnotationProcessorsAnalyzerResult.newBuilder()
        .addAllAnnotationProcessorsData(
          annotationProcessorsAnalyzerResult.annotationProcessorsData.map(::transformAnnotationProcessorsDatum))
        .addAllNonIncrementalAnnotationProcessorsData(
          annotationProcessorsAnalyzerResult.nonIncrementalAnnotationProcessorsData.map(::transformAnnotationProcessorsDatum))
        .build()

    fun construct(
      annotationProcessorsAnalyzerResult: AnnotationProcessorsAnalyzerResult
    ): AnnotationProcessorsAnalyzer.Result = AnnotationProcessorsAnalyzer.Result(
      constructAnnotationProcessorsData(annotationProcessorsAnalyzerResult.annotationProcessorsDataList),
      constructAnnotationProcessorsData(annotationProcessorsAnalyzerResult.nonIncrementalAnnotationProcessorsDataList)
    )

    private fun transformAnnotationProcessorsDatum(annotationProcessorData: AnnotationProcessorData) =
      AnnotationProcessorsAnalyzerResult.AnnotationProcessorsData.newBuilder()
        .setClassName(annotationProcessorData.className)
        .setCompilationDuration(transformDuration(annotationProcessorData.compilationDuration))
        .build()

    private fun transformDuration(duration: Duration): BuildAnalysisResultsMessage.Duration =
      BuildAnalysisResultsMessage.Duration.newBuilder()
        .setSeconds(duration.seconds)
        .setNanos(duration.nano)
        .build()

    private fun constructAnnotationProcessorsData(
      annotationProcessorData: MutableList<AnnotationProcessorsAnalyzerResult.AnnotationProcessorsData>
    ): MutableList<AnnotationProcessorData> {
      val annotationProcessorDataConverted = mutableListOf<AnnotationProcessorData>()
      for (annotationProcessorsDatum in annotationProcessorData) {
        val value = annotationProcessorsDatum.className
        val compilationDuration = annotationProcessorsDatum.compilationDuration
        annotationProcessorDataConverted.add(
          AnnotationProcessorData(value, Duration.ofSeconds(compilationDuration.seconds, compilationDuration.nanos.toLong())))
      }
      return annotationProcessorDataConverted
    }
  }
}