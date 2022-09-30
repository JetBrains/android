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

import com.android.SdkConstants
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.getSuccessfulResult
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class NoncacheableTasksAnalyzerTest {
  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  private fun runTest(testAction: TestContext.() -> Unit) {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    FileUtil.appendToFile(FileUtils.join(preparedProject.root, "app", SdkConstants.FN_BUILD_GRADLE), """
      task sample {
          doLast {
              // do nothing
          }
      }

      afterEvaluate { project ->
          android.applicationVariants.all { variant ->
              def mergeResourcesTask = tasks.getByPath("merge${"$"}{variant.name.capitalize()}Resources")
              mergeResourcesTask.dependsOn sample
          }
      }
    """.trimIndent())

    preparedProject.runTest {
      testAction()
    }
  }

  @Test
  @Ignore("b/144419681")
  fun testNoncacheableTasksAnalyzer() {
    runTest {
      invokeTasks("assembleDebug")

      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()

      assertThat(results.getNonCacheableTasks().size == 1)
      val noncacheableTask = results.getNonCacheableTasks()[0]

      assertThat(noncacheableTask.getTaskPath()).isEqualTo(":app:sample")
      assertThat(noncacheableTask.taskType).isEqualTo("org.gradle.api.DefaultTask")
      assertThat(noncacheableTask.originPlugin.toString()).isEqualTo("script :app:build.gradle")
    }
  }

  @Test
  @Ignore("b/144419681, b/179137380")
  fun testNoncacheableTasksAnalyzerWithSuppressedWarning() {
    runTest {
      BuildAttributionWarningsFilter.getInstance(project).suppressNoncacheableTaskWarning("org.gradle.api.DefaultTask",
                                                                                          ":app:build.gradle")

      invokeTasks("assembleDebug")

      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()

      assertThat(results.getNonCacheableTasks()).isEmpty()
    }
  }
}
