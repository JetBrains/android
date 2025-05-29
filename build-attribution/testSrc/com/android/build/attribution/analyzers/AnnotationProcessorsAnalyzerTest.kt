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

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.getSuccessfulResult
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AnnotationProcessorsAnalyzerTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testNonIncrementalAnnotationProcessorsAnalyzer() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    preparedProject.runTest {
      invokeTasks(":app:compileDebugJavaWithJavac")
      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      var results = buildAnalyzerStorageManager.getSuccessfulResult()

      assertThat(
        results.getNonIncrementalAnnotationProcessorsData().map { it.className }).containsExactlyElementsIn(
        setOf(
          "com.google.auto.value.processor.AutoAnnotationProcessor",
          "com.google.auto.value.processor.AutoValueBuilderProcessor",
          "com.google.auto.value.processor.AutoOneOfProcessor",
          "com.google.auto.value.processor.AutoValueProcessor",
          "com.google.auto.value.extension.memoized.processor.MemoizedValidator"
        )
      )
      assertThat(results.getTaskCategoryWarningsAnalyzerResult()).isInstanceOf(TaskCategoryWarningsAnalyzer.IssuesResult::class.java)
      assertThat(
        (results.getTaskCategoryWarningsAnalyzerResult() as TaskCategoryWarningsAnalyzer.IssuesResult).taskCategoryIssues
      ).contains(
        TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR
      )

      val appBuildFile = FileUtils.join(projectDir, "app", FN_BUILD_GRADLE)

      FileUtils.writeToFile(
        appBuildFile,
        appBuildFile
          .readText()
          .replace("implementation 'com.google.auto.value:auto-value-annotations:1.6.2'", "")
          .replace("annotationProcessor 'com.google.auto.value:auto-value:1.6.2'", "")
      )

      invokeTasks("clean", ":app:compileDebugJavaWithJavac")
      results = buildAnalyzerStorageManager.getSuccessfulResult()

      assertThat(results.getNonIncrementalAnnotationProcessorsData()).isEmpty()
      assertThat(results.getTaskCategoryWarningsAnalyzerResult()).isInstanceOf(TaskCategoryWarningsAnalyzer.IssuesResult::class.java)
      assertThat(
        (results.getTaskCategoryWarningsAnalyzerResult() as TaskCategoryWarningsAnalyzer.IssuesResult).taskCategoryIssues
      ).doesNotContain(
        TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR
      )
    }
  }
}
