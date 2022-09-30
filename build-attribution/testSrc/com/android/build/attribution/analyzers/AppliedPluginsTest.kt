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
package com.android.build.attribution.analyzers

import com.android.SdkConstants
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.getSuccessfulResult
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppliedPluginsTest {

  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  private fun setUpProject() {
    myProjectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      class SamplePlugin implements Plugin<Project> {
          void apply(Project project) {
          }
      }

      apply plugin: SamplePlugin
    """.trimIndent())
  }

  @Test
  fun testAppliedPlugins() {
    setUpProject()

    myProjectRule.invokeTasksRethrowingErrors("assembleDebug")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getSuccessfulResult()

    assertThat(results.getAppliedPlugins()).hasSize(2)
    val appliedPluginsForAppProject =
      results.getAppliedPlugins().toImmutableMap()[":app"]!!.map { it.displayNames().first() }
    assertThat(appliedPluginsForAppProject).containsAllIn(
      listOf("SamplePlugin", "com.android.application", "org.gradle.api.plugins.JavaBasePlugin")
    )
  }
}