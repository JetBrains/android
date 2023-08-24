/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.benchmarks

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Runs the FullProjectBenchmark tests on an old version of the Santa Tracker project which has only Java and XML files.
 *
 * Run locally with:
 * bazel test --test_output=streamed --test_filter=SantaTrackerBenchmark //tools/adt/idea/ide-perf-tests/...
 */
class SantaTrackerBenchmark : FullProjectBenchmark() {
  override val gradleRule = staticRule

  companion object {
    @JvmField
    @ClassRule
    val staticRule = AndroidGradleProjectRule()

    private const val PROJECT_NAME = "SantaTracker"

    @JvmStatic
    @BeforeClass
    fun setUpBeforeClass() {
      loadProject(staticRule, PROJECT_NAME)
    }
  }

  @Test
  fun fullProjectHighlighting() {
    super.fullProjectHighlighting(listOf(JavaFileType.INSTANCE, XmlFileType.INSTANCE), PROJECT_NAME)
  }

  @Test
  fun fullProjectLintInspection() {
    super.fullProjectLintInspection(listOf(JavaFileType.INSTANCE, XmlFileType.INSTANCE), PROJECT_NAME)
  }

  @Test
  fun layoutAttributeCompletion() {
    super.layoutAttributeCompletion(
      LayoutCompletionInput(
        "/santa-tracker/src/main/java/com/google/android/apps/santatracker/games/cityquiz/CityQuizActivity.java",
        "updateScore();|",
        "/santa-tracker/src/main/res/layout/activity_city_quiz.xml",
        "android:id=\"@+id/title_city_quiz\"\n            |"),
      PROJECT_NAME)
  }

  @Test
  fun layoutTagCompletion() {
    super.layoutTagCompletion(
      LayoutCompletionInput(
        "/santa-tracker/src/main/java/com/google/android/apps/santatracker/games/cityquiz/CityQuizActivity.java",
        "updateScore();|",
        "/santa-tracker/src/main/res/layout/activity_city_quiz.xml",
        "<|ProgressBar"),
      PROJECT_NAME)
  }
}