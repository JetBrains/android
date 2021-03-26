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
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class NoncacheableTasksAnalyzerTest {
  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.clearOverride()
  }

  private fun setUpProject() {
    myProjectRule.load(SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      task dummy {
          doLast {
              // do nothing
          }
      }

      afterEvaluate { project ->
          android.applicationVariants.all { variant ->
              def mergeResourcesTask = tasks.getByPath("merge${"$"}{variant.name.capitalize()}Resources")
              mergeResourcesTask.dependsOn dummy
          }
      }
    """.trimIndent())
  }

  @Test
  @Ignore("b/144419681")
  fun testNoncacheableTasksAnalyzer() {
    setUpProject()

    myProjectRule.invokeTasks("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    assertThat(buildAttributionManager.analyzersProxy.getNonCacheableTasks()).hasSize(1)
    val noncacheableTask = buildAttributionManager.analyzersProxy.getNonCacheableTasks()[0]

    assertThat(noncacheableTask.getTaskPath()).isEqualTo(":app:dummy")
    assertThat(noncacheableTask.taskType).isEqualTo("org.gradle.api.DefaultTask")
    assertThat(noncacheableTask.originPlugin.toString()).isEqualTo("script :app:build.gradle")
  }

  @Test
  @Ignore("b/144419681")
  fun testNoncacheableTasksAnalyzerWithSuppressedWarning() {
    setUpProject()

    BuildAttributionWarningsFilter.getInstance(myProjectRule.project).suppressNoncacheableTaskWarning("org.gradle.api.DefaultTask",
                                                                                                      ":app:build.gradle")

    myProjectRule.invokeTasks("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    assertThat(buildAttributionManager.analyzersProxy.getNonCacheableTasks()).isEmpty()
  }
}
