/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.gradleModule
import com.google.common.collect.ImmutableList
import com.google.common.truth.Expect
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class FixNdkVersionProcessorTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  var expect: Expect = Expect.createAndEnableStackTrace()

  @RunsInEdt
  class NonGradle {
    @get:Rule
    val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

    @get:Rule
    var expect: Expect = Expect.createAndEnableStackTrace()

    @Test
    fun testUsageViewDescriptor() {
      val processor = FixNdkVersionProcessor(projectRule.project, ImmutableList.of(), "77.7.7")
      val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
      expect.that(usageDescriptor.getCodeReferencesText(1, 1)).isEqualTo("Values to update " + UsageViewBundle.getReferencesString(1, 1))
      expect.that(usageDescriptor.processedElementsHeader).isEqualTo("Update Android NDK Versions")
    }

    @Test
    fun testUpdateUsageViewDescriptor() {
      val processor = FixNdkVersionProcessor(projectRule.project, ImmutableList.of(), "77.7.7")
      val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
      expect.that(usageDescriptor.getCodeReferencesText(1, 1)).isEqualTo("Values to update " + UsageViewBundle.getReferencesString(1, 1))
      expect.that(usageDescriptor.processedElementsHeader).isEqualTo("Update Android NDK Versions")
    }
  }

  @Test
  fun testFindUsages() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.HELLO_JNI, ndkVersion = SdkConstants.NDK_DEFAULT_VERSION)
    preparedProject.open { project ->
      val module = project.gradleModule(":app")!!
      val file = GradleUtil.getGradleBuildFile(module)!!

      val processor = FixNdkVersionProcessor(project, ImmutableList.of(file), "77.7.7")
      val usages = processor.findUsages()
      expect.that(usages).hasLength(1)
      expect.that(usages.getOrNull(0)?.element?.text).isEqualTo("\"${SdkConstants.NDK_DEFAULT_VERSION}\"")
    }
  }

  @Test
  fun testPerformRefactoring() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.HELLO_JNI, ndkVersion = SdkConstants.NDK_DEFAULT_VERSION)
    preparedProject.open { project ->
      val module = project.gradleModule(":app")!!
      val file = GradleUtil.getGradleBuildFile(module)!!

      val processor = FixNdkVersionProcessor(project, ImmutableList.of(file), "77.7.7")
      val usages = processor.findUsages()
      var synced = false
      GradleSyncState.subscribe(project, object : GradleSyncListener {
        override fun syncFailed(project: Project, errorMessage: String) {
          // It fails with 77.7.7.
          synced = true
        }
      })

      WriteCommandAction.runWriteCommandAction(project) {
        processor.performRefactoring(usages)
      }
      expect.that(String(file.contentsToByteArray()).contains("ndkVersion"))
      expect.that(String(file.contentsToByteArray()).contains("77.7.7"))
      expect.that(synced).isTrue()
    }
  }

}