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
package com.android.tools.idea.gradle.project.sync.issues.processor.runsGradle

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.issues.processor.SuppressUnsupportedSdkVersionPropertyProcessor
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SuppressUnsupportedSdkVersionPropertyProcessorTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun `test usage view descriptor`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val processor = SuppressUnsupportedSdkVersionPropertyProcessor(project, "UpsideDownCake")
      val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)

      assertThat(usageDescriptor.getCodeReferencesText(1, 1))
        .isEqualTo("References to be updated or added: " + UsageViewBundle.getReferencesString(1, 1))
      assertThat(usageDescriptor.processedElementsHeader)
        .isEqualTo("Updating or adding android.suppressUnsupportedCompileSdk gradle property")
    }
  }

  @Test
  fun `test with existing key in gradle properties file`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->

      val gradlePropertiesFile = project.baseDir.findChild("gradle.properties")!!
      runWriteAction {
        gradlePropertiesFile.setBinaryContent("""
          android.suppressUnsupportedCompileSdk=33
      """.trimIndent().toByteArray(Charsets.UTF_8))
      }
      assertThat(gradlePropertiesFile.exists()).isTrue()

      val processor = SuppressUnsupportedSdkVersionPropertyProcessor(project, "33,UpsideDownCake")

      val usages = processor.findUsages()
      assertThat(usages).hasLength(1)
      // Psi element points to the content of the line
      assertThat(usages.getOrNull(0)?.element?.text).isEqualTo("android.suppressUnsupportedCompileSdk=33")

      var synced = false
      GradleSyncState.subscribe(project, object : GradleSyncListener {
        override fun syncSucceeded(project: Project) {
          synced = true
        }
      })
      WriteCommandAction.runWriteCommandAction(project) {
        processor.performRefactoring(usages)
      }

      assertThat(String(gradlePropertiesFile.contentsToByteArray()).contains("android.suppressUnsupportedCompileSdk=33,UpsideDownCake")).isTrue()
      assertThat(synced).isTrue()
    }
  }

  @Test
  fun `test with missing key in gradle properties file`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val gradlePropertiesFile = project.baseDir.findChild("gradle.properties")!!
      assertThat(gradlePropertiesFile.exists()).isTrue()

      val processor = SuppressUnsupportedSdkVersionPropertyProcessor(project, "UpsideDownCake")

      val usages = processor.findUsages()
      assertThat(usages).hasLength(1)
      // Psi element points to contents of the file
      assertThat(usages.getOrNull(0)?.element?.text).isEqualTo(String(gradlePropertiesFile.contentsToByteArray()))

      var synced = false
      GradleSyncState.subscribe(project, object : GradleSyncListener {
        override fun syncSucceeded(project: Project) {
          synced = true
        }
      })
      WriteCommandAction.runWriteCommandAction(project) {
        processor.performRefactoring(usages)
      }

      assertThat(String(gradlePropertiesFile.contentsToByteArray()).contains("android.suppressUnsupportedCompileSdk=UpsideDownCake")).isTrue()
      assertThat(synced).isTrue()
    }
  }

  @Test
  fun `test with gradle properties file not existing`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      var gradlePropertiesFile = project.baseDir.findChild("gradle.properties")!!

      runWriteAction {
        gradlePropertiesFile.delete(this)
      }
      val processor = SuppressUnsupportedSdkVersionPropertyProcessor(project, "UpsideDownCake")

      val usages = processor.findUsages()
      assertThat(usages).hasLength(1)
      // Psi element points to empty string as the file does not exist
      assertThat(usages.getOrNull(0)?.element?.text).isEqualTo("")

      var synced = false
      GradleSyncState.subscribe(project, object : GradleSyncListener {
        override fun syncSucceeded(project: Project) {
          synced = true
        }
      })
      WriteCommandAction.runWriteCommandAction(project) {
        processor.performRefactoring(usages)
      }

      gradlePropertiesFile = project.baseDir.findChild("gradle.properties")!!
      assertThat(gradlePropertiesFile.exists()).isTrue()
      assertThat(String(gradlePropertiesFile.contentsToByteArray()).contains("android.suppressUnsupportedCompileSdk=UpsideDownCake")).isTrue()
      assertThat(synced).isTrue()
    }
  }
}