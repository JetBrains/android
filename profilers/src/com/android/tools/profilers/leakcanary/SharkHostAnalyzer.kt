/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.leakcanary

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import shark.AndroidObjectInspectors
import shark.AndroidReferenceMatchers
import shark.HeapAnalysis
import shark.HeapAnalysisException
import shark.HeapAnalysisFailure
import shark.HeapAnalyzer
import shark.HprofHeapGraph.Companion.openHeapGraph
import shark.KeyedWeakReferenceFinder


class SharkHostAnalyzer {

  companion object {
    private val logger = Logger.getInstance(SharkHostAnalyzer::class.java)
  }

  /**
   * Takes a heap dump file and returns a structured analysis result.
   *
   * @param hprofFile The .hprof file to analyze.
   * @return A [shark.HeapAnalysis] object which can be a [shark.HeapAnalysisSuccess] or [shark.HeapAnalysisFailure].
   */
  fun analyze(hprofFile: File): HeapAnalysis {
    var analysisResult: HeapAnalysis
    try {
      val analyzer = HeapAnalyzer { step -> logger.info(step.toString()) }
      analysisResult = analyzer.analyze(
        heapDumpFile = hprofFile,
        graph = hprofFile.openHeapGraph(),
        leakingObjectFinder = KeyedWeakReferenceFinder,
        referenceMatchers = AndroidReferenceMatchers.Companion.appDefaults,
        computeRetainedHeapSize = true,
        objectInspectors = AndroidObjectInspectors.Companion.appDefaults,
      )
      logger.info("Leak analysis complete : $analysisResult")
    }
    catch (e: Throwable) {
      logger.warn("Heap analysis failed for ${hprofFile.name}", e)
      analysisResult = HeapAnalysisFailure(
        heapDumpFile = hprofFile,
        createdAtTimeMillis = System.currentTimeMillis(),
        analysisDurationMillis = 0, // Analysis didn't run
        exception = HeapAnalysisException(e)
      )
    }
    return analysisResult
  }
}