/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.truth.Truth
import org.junit.Test

class BaseAnalyzerTest {

  class Result : AnalyzerResult

  @Test
  fun testResultCached() {
    var calculateCallsCount = 0

    val analyzer =  object : BaseAnalyzer<Result>() {
      override fun calculateResult(): Result {
        calculateCallsCount++
        return Result()
      }

      override fun cleanupTempState() = Unit
    }

    analyzer.onBuildStart()
    val result1 = analyzer.result
    val result2 = analyzer.result

    // Should be called on start and after compute.
    Truth.assertThat(calculateCallsCount).isEqualTo(1)
    Truth.assertThat(result1).isSameAs(result2)
  }

  @Test
  fun testCleanupCalledAfterResultCalculated() {
    var cleanUpCallsCount = 0

    val analyzer =  object : BaseAnalyzer<Result>() {
      override fun calculateResult(): Result = Result()

      override fun cleanupTempState() {
        cleanUpCallsCount++
      }
    }

    analyzer.onBuildStart()
    analyzer.result

    // Should be called on start and after compute.
    Truth.assertThat(cleanUpCallsCount).isEqualTo(2)
  }

  @Test
  fun testCleanupCalledAfterBuildFailed() {
    var cleanUpCallsCount = 0

    val analyzer =  object : BaseAnalyzer<Result>() {
      override fun calculateResult(): Result = Result()

      override fun cleanupTempState() {
        cleanUpCallsCount++
      }
    }

    analyzer.onBuildStart()
    analyzer.onBuildFailure()

    // Should be called on start and on fail.
    Truth.assertThat(cleanUpCallsCount).isEqualTo(2)
  }

  @Test(expected = BaseAnalyzer.ResultComputationLoopException::class)
  fun testComputationLoopDetected() {
    class AnalyzerWithDependency : BaseAnalyzer<Result>() {
      var dependency : BaseAnalyzer<Result>? = null
      override fun calculateResult(): Result {
        return dependency?.result ?: Result()
      }

      override fun cleanupTempState() = Unit
    }

    val analyzer1 = AnalyzerWithDependency()
    val analyzer2 = AnalyzerWithDependency()

    analyzer1.dependency = analyzer2
    analyzer2.dependency = analyzer1

    analyzer2.result
  }
}