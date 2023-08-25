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

import com.android.tools.idea.benchmarks.FullProjectBenchmark
import com.android.tools.idea.benchmarks.LayoutCompletionInput
import com.android.tools.idea.benchmarks.runBenchmark
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.descendantsOfType
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

// Runs simplified versions of the perfgate benchmarks in SantaTrackerKotlinBenchmark
// to catch breakages in presubmit.
class SantaTrackerKotlinBenchmarkTest : FullProjectBenchmark() {
  override val gradleRule = staticRule

  companion object {
    @JvmField
    @ClassRule
    val staticRule = AndroidGradleProjectRule()

    private const val PROJECT_NAME = "SantaTrackerKotlin"

    @JvmStatic
    @BeforeClass
    fun setUpBeforeClass() {
      loadProject(staticRule, PROJECT_NAME)
    }
  }

  // Only highlight one file
  @Test
  fun fullProjectHighlighting() {
    val fileTypes = listOf(JavaFileType.INSTANCE, KotlinFileType.INSTANCE as LanguageFileType, XmlFileType.INSTANCE)
    runInEdtAndWait {
      for (fileType in fileTypes) {
        measureHighlighting(
          fileType,
          PROJECT_NAME,
          maxFiles = 1,
          doWarmup = false,
          doLogging = false
        )
      }
    }
  }

  // Only run inspection on one file
  @Test
  fun fullProjectLintInspection() {
    val fileTypes = listOf(JavaFileType.INSTANCE, XmlFileType.INSTANCE)
    runInEdtAndWait {
      for (fileType in fileTypes) {
        measureLintInspections(
          fileType,
          PROJECT_NAME,
          maxFiles = 1,
          doWarmup = false,
          doLogging = false
        )
      }
    }
  }

  // No warmup, only one iteration
  @Test
  fun layoutAttributeCompletion() {
    val input = LayoutCompletionInput(
      "/cityquiz/src/main/java/com/google/android/apps/santatracker/cityquiz/CityQuizActivity.kt",
      "updateScore()\n|",
      "/cityquiz/src/main/res/layout/activity_city_quiz.xml",
      "android:id=\"@+id/title_city_quiz\"\n            |"
    )

    runBenchmark(
      recordResults = { runLayoutEditingCuj(input) },
      runBetweenIterations = { clearCaches() },
      commitResults = { },
      warmupIterations = 0,
      mainIterations = 1
    )
  }

  // No warmup, only one iteration
  @Test
  fun layoutTagCompletion() {
    val input = LayoutCompletionInput(
      "/cityquiz/src/main/java/com/google/android/apps/santatracker/cityquiz/CityQuizActivity.kt",
      "updateScore()\n|",
      "/cityquiz/src/main/res/layout/activity_city_quiz.xml",
      "<|ProgressBar"
    )

    runBenchmark(
      recordResults = { runLayoutEditingCuj(input) },
      runBetweenIterations = { clearCaches() },
      commitResults = { },
      warmupIterations = 0,
      mainIterations = 1
    )
  }

  // No warmup, only one file
  @Test
  fun kotlinTopLevelCompletion() {
    runBenchmark(
      collectElements = {
        collectSuitableFiles(KotlinFileType.INSTANCE as FileType, ProjectScope.getContentScope(gradleRule.project), 1)
      },
      warmupAction = { },
      benchmarkAction = { performTopLevelCompletionForFile(it) },
      commitResults = { }
    )
  }

  // No warmup, only one file
  @Test
  fun kotlinLocalLevelCompletion() {
    runBenchmark(
      collectElements = {
        // Find a file with a suitable function to do local completion in
        listOf(
          collectSuitableFiles(KotlinFileType.INSTANCE as FileType, ProjectScope.getContentScope(gradleRule.project), 50).first { file ->
            PsiManager.getInstance(gradleRule.project).findFile(file)
              ?.descendantsOfType<KtFunction>()?.filter { it.hasBlockBody() && it.bodyExpression != null }?.firstOrNull() != null
          }
        )
      },
      warmupAction = { },
      benchmarkAction = { performLocalCompletionForFile(it, 5) },
      commitResults = { }
    )
  }
}