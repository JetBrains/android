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

import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils
import com.android.tools.idea.gradle.project.sync.utils.ProjectJdkUtils
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import io.ktor.util.reflect.instanceOf
import java.io.File
import kotlin.reflect.KClass

class AssertInMemoryConfig(
  private val syncedProject: Project,
  private val expect: Expect
) {
  fun assertGradleJdk(expectedJdkName: String) {
    val currentGradleJdkName = ProjectJdkUtils.getGradleRootJdkNameInMemory(syncedProject, "")
    expect.that(currentGradleJdkName).isEqualTo(expectedJdkName)
  }

  fun assertGradleRootsJdk(expectedGradleRootsJdkName: Map<String, String>) {
    expectedGradleRootsJdkName.forEach { (gradleRootPath, expectedJdkName) ->
      val currentGradleRootJdkName = ProjectJdkUtils.getGradleRootJdkNameInMemory(syncedProject, gradleRootPath)
      expect.that("$gradleRootPath:$currentGradleRootJdkName").isEqualTo("$gradleRootPath:$expectedJdkName")
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

  fun assertGradleExecutionDaemon(expectedJdkPath: String) {
    val currentJdkPath = ProjectJdkUtils.getGradleDaemonExecutionJdkPath(syncedProject)
    expect.that(currentJdkPath).isEqualTo(expectedJdkPath)
  }
}

class AssertOnDiskConfig(
  private val syncedProject: Project,
  private val expect: Expect
) {
  fun assertGradleJdk(expectedJdkName: String?) {
    val projectFile = File(syncedProject.basePath.orEmpty())
    val currentGradleJdkName = ProjectJdkUtils.getGradleRootJdkNameFromIdeaGradleXmlFile(projectFile, "")
    expect.that(currentGradleJdkName).isEqualTo(expectedJdkName)
  }

  fun assertGradleRootsJdk(expectedGradleRootsJdkName: Map<String, String>) {
    expectedGradleRootsJdkName.forEach { (gradleRootPath, expectedJdkName) ->
      val projectFile = File(syncedProject.basePath.orEmpty())
      val currentGradleRootJdkName = ProjectJdkUtils.getGradleRootJdkNameFromIdeaGradleXmlFile(projectFile, gradleRootPath)
      expect.that("$gradleRootPath:$currentGradleRootJdkName").isEqualTo("$gradleRootPath:$expectedJdkName")
    }
  }

  fun assertProjectJdk(expectedJdkName: String) {
    val projectRoot = File(syncedProject.basePath.orEmpty())
    val currentJdkName = ProjectJdkUtils.getProjectJdkNameInIdeaXmlFile(projectRoot)
    expect.that(currentJdkName).isEqualTo(expectedJdkName)
  }
}

class AssertOnFailure(
  private val exception: Exception,
  private val expect: Expect
) {
  fun assertException(expectedException: KClass<out Exception>) {
    expect.that(exception).instanceOf(expectedException)
  }
}