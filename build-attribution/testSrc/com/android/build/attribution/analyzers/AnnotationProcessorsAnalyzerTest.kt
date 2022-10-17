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
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class AnnotationProcessorsAnalyzerTest {

  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  private fun setUpProject() {
    myProjectRule.load(SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", FN_BUILD_GRADLE), """
      dependencies {
        implementation 'com.google.auto.value:auto-value-annotations:1.6.2'
        annotationProcessor 'com.google.auto.value:auto-value:1.6.2'
      }
    """.trimIndent())
  }

  @Test
  fun testNonIncrementalAnnotationProcessorsAnalyzer() {
    setUpProject()

    myProjectRule.invokeTasksRethrowingErrors(":app:compileDebugJavaWithJavac")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()

    assertThat(
      results.getAnnotationProcessorsData().map { it.className }).containsExactlyElementsIn(
      setOf(
        "com.google.auto.value.processor.AutoAnnotationProcessor",
        "com.google.auto.value.processor.AutoValueBuilderProcessor",
        "com.google.auto.value.processor.AutoOneOfProcessor",
        "com.google.auto.value.processor.AutoValueProcessor",
        "com.google.auto.value.extension.memoized.processor.MemoizedValidator"
      )
    )
  }

  @Test
  @Ignore("b/179137380")
  fun testNonIncrementalAnnotationProcessorsAnalyzerWithSuppressedWarnings() {
    setUpProject()

    BuildAttributionWarningsFilter.getInstance(myProjectRule.project).suppressNonIncrementalAnnotationProcessorWarning(
      "com.google.auto.value.processor.AutoAnnotationProcessor")
    BuildAttributionWarningsFilter.getInstance(myProjectRule.project).suppressNonIncrementalAnnotationProcessorWarning(
      "com.google.auto.value.processor.AutoValueProcessor")

    myProjectRule.invokeTasksRethrowingErrors(":app:compileDebugJavaWithJavac")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()

    assertThat(
      results.getAnnotationProcessorsData().map { it.className }).containsExactlyElementsIn(
      setOf(
        "com.google.auto.value.processor.AutoValueBuilderProcessor",
        "com.google.auto.value.processor.AutoOneOfProcessor",
        "com.google.auto.value.extension.memoized.processor.MemoizedValidator"
      )
    )
  }

  @Test
  fun noWarningsWhenApplyingKapt() {
    setUpProject()

    val rootBuildFile = FileUtils.join(File(myProjectRule.project.basePath!!), FN_BUILD_GRADLE)
    FileUtil.writeToFile(rootBuildFile,
                         rootBuildFile
                           .readText()
                           .replace("dependencies {",
                                    "dependencies { classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$KOTLIN_VERSION_FOR_TESTS\""))
    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", FN_BUILD_GRADLE), """

      apply plugin: 'kotlin-android'
      apply plugin: 'kotlin-kapt'
    """.trimIndent())

    myProjectRule.invokeTasksRethrowingErrors(":app:compileDebugJavaWithJavac")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()

    assertThat(results.getAnnotationProcessorsData().isEmpty())
  }
}