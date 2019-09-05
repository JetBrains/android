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

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult
import java.time.Duration

/**
 * Analyzer for reporting non incremental annotation processors and the annotation processors compilation time.
 */
class AnnotationProcessorsAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter) : BuildEventsAnalyzer {
  private val annotationProcessorsMap = HashMap<String, Duration>()
  private val nonIncrementalAnnotationProcessorsSet = HashSet<String>()

  /**
   * Sums up the compilation time for annotation processors for all sub-projects.
   */
  private fun updateAnnotationProcessorCompilationTime(className: String,
                                                       compilationDuration: Duration) {
    val currentDuration = annotationProcessorsMap.getOrDefault(className, Duration.ZERO)
    annotationProcessorsMap[className] = currentDuration + compilationDuration
  }

  override fun receiveEvent(event: ProgressEvent) {
    if (event is TaskFinishEvent) {
      val result = event.result
      if (result is JavaCompileTaskOperationResult) {
        result.annotationProcessorResults?.forEach {
          updateAnnotationProcessorCompilationTime(it.className, it.duration)

          if (it.type == JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.UNKNOWN &&
              warningsFilter.applyNonIncrementalAnnotationProcessorFilter(it.className)) {
            nonIncrementalAnnotationProcessorsSet.add(it.className)
          }
        }
      }
    }
  }

  override fun onBuildStart() {
    annotationProcessorsMap.clear()
    nonIncrementalAnnotationProcessorsSet.clear()
  }

  override fun onBuildSuccess(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData?) {
    // nothing to be done
  }

  override fun onBuildFailure() {
    annotationProcessorsMap.clear()
    nonIncrementalAnnotationProcessorsSet.clear()
  }

  fun getAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorsMap.map { AnnotationProcessorData(it.key, it.value) }
  }

  fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return nonIncrementalAnnotationProcessorsSet.map { AnnotationProcessorData(it, annotationProcessorsMap[it]!!) }
  }

  data class AnnotationProcessorData constructor(val className: String, val compilationDuration: Duration)
}
