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
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.toml.lang.psi.TomlFileType

/**
 * Runs the FullProjectBenchmark tests on JetNews project.
 *
 * Run locally with:
 * bazel test --test_output=streamed --test_filter=JetNewsBenchmark //tools/adt/idea/ide-perf-tests/...
 */
class JetNewsBenchmark : FullProjectBenchmark() {
  override val gradleRule = staticRule

  companion object {
    @JvmField
    @ClassRule
    val staticRule = AndroidGradleProjectRule()

    private const val PROJECT_NAME = "JetNews"

    @JvmStatic
    @BeforeClass
    fun setUpBeforeClass() {
      loadProject(staticRule, PROJECT_NAME)
      assert(KotlinPluginModeProvider.isK1Mode())
    }
  }

  @Test
  fun fullProjectHighlighting() {
    super.fullProjectHighlighting(listOf(KotlinFileType.INSTANCE, TomlFileType), PROJECT_NAME)
  }

  @Test
  fun fullProjectLintInspection() {
    super.fullProjectLintInspection(PROJECT_NAME)
  }

  @Test
  fun kotlinTopLevelCompletion() {
    super.testTopLevelCompletionForKotlin(PROJECT_NAME)
  }

  @Test
  fun kotlinLocalLevelCompletion() {
    super.testLocalLevelCompletionForKotlin(PROJECT_NAME)
  }
}