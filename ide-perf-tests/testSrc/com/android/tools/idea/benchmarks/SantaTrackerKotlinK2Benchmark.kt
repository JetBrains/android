/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class SantaTrackerKotlinK2Benchmark : FullProjectBenchmark() {
  override val gradleRule = staticRule

  companion object {
    init {
      System.setProperty("idea.kotlin.plugin.use.k2", "true")
    }

    @JvmField
    @ClassRule
    val staticRule = AndroidGradleProjectRule()

    private const val GRADLE_PROJECT_NAME = "SantaTrackerKotlin"
    private var PROJECT_NAME = "SantaTrackerKotlin_K2"
    @JvmStatic
    @BeforeClass
    fun setUpBeforeClass() {
      loadProject(staticRule, GRADLE_PROJECT_NAME)
      assert(KotlinPluginModeProvider.isK2Mode())
    }
  }

  @Test
  fun fullProjectHighlighting() {
    super.fullProjectHighlighting(listOf(JavaFileType.INSTANCE, KotlinFileType.INSTANCE as LanguageFileType, XmlFileType.INSTANCE), PROJECT_NAME)
  }

  @Test
  fun fullProjectLintInspection() {
    super.fullProjectLintInspection(listOf(JavaFileType.INSTANCE, KotlinFileType.INSTANCE as LanguageFileType, XmlFileType.INSTANCE), PROJECT_NAME)
  }

  @Test
  fun kotlinTopLevelCompletion() {
    super.testTopLevelCompletionForKotlin(PROJECT_NAME)
  }

  @Test
  fun kotlinLocalLevelCompletion() {
    super.testLocalLevelCompletionForKotlin(PROJECT_NAME)
  }

  @Test
  fun layoutTagCompletion() {
    super.layoutTagCompletion(
      LayoutCompletionInput(
        "/cityquiz/src/main/java/com/google/android/apps/santatracker/cityquiz/CityQuizActivity.kt",
        "updateScore()\n|",
        "/cityquiz/src/main/res/layout/activity_city_quiz.xml",
        "<|ProgressBar"
      ),
      PROJECT_NAME)
  }
}