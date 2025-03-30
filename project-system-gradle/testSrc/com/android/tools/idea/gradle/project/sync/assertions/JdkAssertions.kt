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
package com.android.tools.idea.gradle.project.sync.assertions

import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause
import com.android.tools.idea.gradle.project.sync.model.ExpectedGradleRoot
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils
import com.android.tools.idea.gradle.project.sync.utils.ProjectJdkUtils
import com.android.tools.idea.gradle.service.notification.OpenProjectJdkLocationListener
import com.android.tools.idea.gradle.service.notification.UseJdkAsProjectJdkListener
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.io.File
import kotlin.reflect.KClass

class AssertInMemoryConfig(
  private val syncedProject: Project,
  private val expect: Expect
) {

  private val projectFile by lazy { File(syncedProject.basePath.orEmpty()) }

  fun assertGradleJdk(expectedJdkName: String, gradleRootName: String = "") {
    val currentGradleRootJdkName = ProjectJdkUtils.getGradleRootJdkNameInMemory(syncedProject, gradleRootName)
    expect.that("$gradleRootName:$currentGradleRootJdkName").isEqualTo("$gradleRootName:$expectedJdkName")
  }

  fun assertGradleRoots(expectedGradleRoots: Map<String, ExpectedGradleRoot>) {
    expectedGradleRoots.forEach { (gradleRootName, expectedGradleRoot) ->
      expectedGradleRoot.ideaGradleJdk?.let { expectedJdkName ->
        assertGradleJdk(expectedJdkName, gradleRootName)
      }
      expectedGradleRoot.gradleExecutionDaemonJdkPath?.let { expectedJdkPath ->
        assertGradleExecutionDaemon(expectedJdkPath, gradleRootName)
      }
    }
  }

  fun assertProjectJdkAndValidateTableEntry(expectedJdkName: String, expectedJdkPath: String) {
    assertProjectJdk(expectedJdkName)
    assertProjectJdkTablePath(expectedJdkPath)
    assertProjectJdkTableEntryIsValid(expectedJdkName)
  }

  fun assertProjectJdk(expectedJdkName: String) {
    val currentJdkName = ProjectJdkUtils.getProjectJdkNameInMemory(syncedProject)
    expect.that(currentJdkName).isEqualTo(expectedJdkName)
  }

  fun assertProjectJdkTablePath(expectedJdkPath: String) {
    val currentJdkName = ProjectJdkUtils.getProjectJdkNameInMemory(syncedProject).orEmpty()
    val currentJdkPath = JdkTableUtils.getJdkPathFromJdkTable(currentJdkName)
    expect.that(currentJdkPath).isEqualTo(expectedJdkPath)
  }

  fun assertProjectJdkTableEntryIsValid(jdkName: String) {
    val containsValidJdkEntry = JdkTableUtils.containsValidJdkTableEntry(jdkName)
    expect.that(containsValidJdkEntry).isTrue()
  }

  fun assertGradleExecutionDaemon(expectedJdkPath: String, gradleRootName: String = "") {
    val gradleRootFile = projectFile.resolve(gradleRootName)
    val currentJdkPath = ProjectJdkUtils.getGradleDaemonExecutionJdkPath(syncedProject, gradleRootFile.toString())
    expect.that("$gradleRootName:$currentJdkPath").isEqualTo("$gradleRootName:$expectedJdkPath")
  }
}

class AssertOnDiskConfig(
  private val syncedProject: Project,
  private val expect: Expect
) {

  private val projectFile by lazy { File(syncedProject.basePath.orEmpty()) }

  fun assertGradleJdk(expectedJdkName: String?, gradleRootName: String = "") {
    val currentGradleRootJdkName = ProjectJdkUtils.getGradleRootJdkNameFromIdeaGradleXmlFile(projectFile, gradleRootName)
    expect.that("$gradleRootName:$currentGradleRootJdkName").isEqualTo("$gradleRootName:$expectedJdkName")
  }

  fun assertGradleRoots(expectedGradleRoots: Map<String, ExpectedGradleRoot>) {
    expectedGradleRoots.forEach { (gradleRootName, expectedGradleRoot) ->
      expectedGradleRoot.ideaGradleJdk?.let { expectedJdkName ->
        assertGradleJdk(expectedJdkName, gradleRootName)
      }
      expectedGradleRoot.gradleLocalJavaHome?.let { expectedJavaHome ->
        assertGradleLocalJavaHome(expectedJavaHome, gradleRootName)
      }
    }
  }

  fun assertProjectJdk(expectedJdkName: String) {
    val currentJdkName = ProjectJdkUtils.getProjectJdkNameInIdeaXmlFile(projectFile)
    expect.that(currentJdkName).isEqualTo(expectedJdkName)
  }

  fun assertGradleLocalJavaHome(expectedJavaHome: String?, gradleRootName: String = "") {
    val gradleRootFile = projectFile.resolve(gradleRootName)
    val currentGradleLocalJavaHome = GradleConfigProperties(gradleRootFile).javaHome
    expect.that("$gradleRootName:$currentGradleLocalJavaHome").isEqualTo("$gradleRootName:$expectedJavaHome")
  }
}

class AssertOnFailure(
  private val exception: Exception,
  private val expect: Expect
) {
  fun assertException(expectedException: KClass<out Exception>) {
    expect.that(exception).isInstanceOf(expectedException::class.java)
  }
}

class AssertSyncEvents(
  private val exceptionSyncMessages: List<String>,
  private val expect: Expect
) {
  fun assertInvalidGradleJdkMessage(expectedInvalidGradleJdk: InvalidGradleJdkCause) {
    val currentException = exceptionSyncMessages.joinToString("\n")
    val expectedException = """
      |${GradleBundle.message("gradle.jvm.is.invalid")}
      |${expectedInvalidGradleJdk.description}
      |<a href="${UseJdkAsProjectJdkListener.baseId()}.embedded">Use Embedded JDK (${IdeSdks.getInstance().embeddedJdkPath})</a>
      |<a href="${OpenProjectJdkLocationListener.ID}">Change Gradle JDK location</a>
    """.trimMargin()
    expect.that(currentException).isEqualTo(expectedException)
  }
}