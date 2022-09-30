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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.getSuccessfulResult
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.gradle.dsl.utils.FN_SETTINGS_GRADLE
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.Companion.builder
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class AnnotationProcessorsAnalyzerTest {

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  interface TestContext {
    val project: Project
    val projectDir: File
    fun executeTasks(vararg task: String)
  }

  private fun runTest(body: TestContext.() -> Unit) {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val projectPath = preparedProject.root

    FileUtil.writeToFile(
      FileUtils.join(projectPath, "lib", FN_BUILD_GRADLE).also {
        it.parentFile.mkdirs()
      },
      """
        apply plugin: 'com.android.library'

        android {
          namespace "google.simplelibrary"
          compileSdkVersion 33
        }
      """.trimIndent()
    )

    FileUtil.writeToFile(
      FileUtils.join(projectPath, "lib", "src", "main", "java", "google", "simplelib", "Test.java").also {
        it.parentFile.mkdirs()
      },
      """
          package google.simplelib;

          class Test { }
        """.trimIndent()
    )

    FileUtil.writeToFile(
      FileUtils.join(projectPath, "lib", "src", "main", FN_ANDROID_MANIFEST_XML).also {
        it.parentFile.mkdirs()
      },
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

        </manifest>
      """.trimIndent()
    )

    FileUtil.appendToFile(
      FileUtils.join(projectPath, "app", FN_BUILD_GRADLE), """
      dependencies {
        implementation project(":lib")
        implementation 'com.google.auto.value:auto-value-annotations:1.6.2'
        annotationProcessor 'com.google.auto.value:auto-value:1.6.2'
      }
    """.trimIndent()
    )

    FileUtil.appendToFile(
      FileUtils.join(projectPath, FN_SETTINGS_GRADLE), """
      include ':lib'
    """.trimIndent()
    )
    preparedProject.open { project ->
      body(
        object : TestContext {
          override val project: Project = project
          override val projectDir: File = preparedProject.root
          override fun executeTasks(vararg task: String) {
            val invocationResult = project.buildAndWait { it.executeTasks(builder(project, projectDir, *task).build()) }
            invocationResult.buildError?.let { throw it }
          }
        }
      )
    }
  }

  @Test
  fun testNonIncrementalAnnotationProcessorsAnalyzer() {
    runTest {
      executeTasks(":app:compileDebugJavaWithJavac")
      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()


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
  }

  @Test
  @Ignore("b/179137380")
  fun testNonIncrementalAnnotationProcessorsAnalyzerWithSuppressedWarnings() {
    runTest {
      BuildAttributionWarningsFilter.getInstance(project).suppressNonIncrementalAnnotationProcessorWarning(
        "com.google.auto.value.processor.AutoAnnotationProcessor"
      )
      BuildAttributionWarningsFilter.getInstance(project).suppressNonIncrementalAnnotationProcessorWarning(
        "com.google.auto.value.processor.AutoValueProcessor"
      )

      executeTasks(":app:compileDebugJavaWithJavac")

      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()

      assertThat(
        results.getAnnotationProcessorsData().map { it.className }).containsExactlyElementsIn(
        setOf(
          "com.google.auto.value.processor.AutoValueBuilderProcessor",
          "com.google.auto.value.processor.AutoOneOfProcessor",
          "com.google.auto.value.extension.memoized.processor.MemoizedValidator"
        )
      )
    }
  }

  @Test
  fun noWarningsWhenApplyingKapt() {
    runTest {
      val rootBuildFile = FileUtils.join(projectDir, FN_BUILD_GRADLE)
      FileUtil.writeToFile(rootBuildFile,
                           rootBuildFile
                             .readText()
                             .replace("dependencies {",
                                      "dependencies { classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$KOTLIN_VERSION_FOR_TESTS\""))
      FileUtil.appendToFile(FileUtils.join(projectDir, "lib", FN_BUILD_GRADLE), """
  
        apply plugin: 'kotlin-android'
        apply plugin: 'kotlin-kapt'
      """.trimIndent())

      executeTasks(":app:compileDebugJavaWithJavac")

      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()

      assertThat(results.getAnnotationProcessorsData().isEmpty())
    }
  }

  @Test
  fun warnWhenApplyingKaptInAppButLibHasNonIncrementalAnnotationProcessors() {
    runTest {
      val rootBuildFile = FileUtils.join(projectDir, FN_BUILD_GRADLE)
      FileUtil.writeToFile(rootBuildFile,
                           rootBuildFile
                             .readText()
                             .replace("dependencies {",
                                      "dependencies { classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$KOTLIN_VERSION_FOR_TESTS\""))
      FileUtil.appendToFile(FileUtils.join(projectDir, "lib", FN_BUILD_GRADLE), """
  
        apply plugin: 'kotlin-android'
        apply plugin: 'kotlin-kapt'
      """.trimIndent())

      FileUtil.appendToFile(FileUtils.join(projectDir, "lib", FN_BUILD_GRADLE), """
  
        dependencies {
          implementation 'com.google.auto.value:auto-value-annotations:1.6.2'
          annotationProcessor 'com.google.auto.value:auto-value:1.6.2'
        }
      """.trimIndent())

      executeTasks(":app:compileDebugJavaWithJavac")

      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()

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
  }
}