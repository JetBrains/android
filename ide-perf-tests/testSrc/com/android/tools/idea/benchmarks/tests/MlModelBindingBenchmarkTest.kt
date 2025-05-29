/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.benchmarks.tests

import com.android.test.testutils.TestUtils
import com.android.tools.idea.benchmarks.MlModelBindingBenchmark
import com.android.tools.idea.benchmarks.measureTimeMs
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Runs simplified versions of the perfgate benchmarks in MlModelBindingBenchmark
// to catch breakages in presubmit.
class MlModelBindingBenchmarkTest {
  @get:Rule
  val gradleRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    // Load project under mlkit directory.
    gradleRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("prebuilts/tools/common/mlkit/testData").toString()
    gradleRule.load("projects/mlModelBindingApplication")
  }

  // No warmup, only one iteration
  @Test
  fun projectHighlighting() {
    MlModelBindingBenchmark.doBenchmark(
      gradleRule,
      "app/src/main/java/google/mlmodelbinding/HighlightActivity.java",
      {
        measureTimeMs(
          warmupIterations = 0,
          mainIterations = 1,
          setUp = {
            PsiManager.getInstance(gradleRule.project).dropPsiCaches()
            System.gc()
          },
          action = {
            val info = gradleRule.fixture.doHighlighting(HighlightSeverity.ERROR)
            assert(info.isEmpty())
          }
        )
      },
      { }
    )
  }

  // No warmup, only one iteration
  @Test
  fun projectCompletion() {
    MlModelBindingBenchmark.doBenchmark(
      gradleRule,
      "app/src/main/java/google/mlmodelbinding/CompleteActivity.java",
      {
        measureTimeMs(
          warmupIterations = 0,
          mainIterations = 1,
          setUp = {
            PsiManager.getInstance(gradleRule.project).dropPsiCaches()
            System.gc()
          },
          action = {
            gradleRule.fixture.complete(CompletionType.BASIC)
          }
        )
      },
      { }
    )
  }
}