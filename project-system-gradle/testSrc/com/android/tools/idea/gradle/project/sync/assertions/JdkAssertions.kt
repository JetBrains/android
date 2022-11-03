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
import kotlin.reflect.KClass

class AssertInMemoryConfig(
  private val syncedProject: Project,
  private val projectJdkUtils: ProjectJdkUtils,
  private val expect: Expect
) {
  fun assertGradleJdk(expectedJdkName: String) {
    val currentJdkName = projectJdkUtils.getGradleJdkNameInMemory(syncedProject)
    expect.that(currentJdkName).isEqualTo(expectedJdkName)
  }

  fun assertProjectJdk(expectedJdkName: String) {
    val currentJdkName = projectJdkUtils.getProjectJdkNameInMemory(syncedProject)
    expect.that(currentJdkName).isEqualTo(expectedJdkName)
  }

  fun assertProjectJdkTablePath(expectedJdkPath: String) {
    val currentJdkName = projectJdkUtils.getProjectJdkNameInMemory(syncedProject).orEmpty()
    val currentJdkPath = JdkTableUtils.getJdkPathFromJdkTable(currentJdkName)
    expect.that(currentJdkPath).isEqualTo(expectedJdkPath)
  }

  fun assertProjectJdkTableEntryIsValid(jdkName: String) {
    val containsValidJdkEntry = JdkTableUtils.containsValidJdkTableEntry(jdkName)
    expect.that(containsValidJdkEntry).isTrue()
  }

  fun assertGradleExecutionDaemon(expectedJdkPath: String) {
    val currentJdkPath = projectJdkUtils.getGradleDaemonExecutionJdkPath(syncedProject)
    expect.that(currentJdkPath).isEqualTo(expectedJdkPath)
  }
}

class AssertOnDiskConfig(
  private val projectJdkUtils: ProjectJdkUtils,
  private val expect: Expect
) {
  fun assertGradleJdk(expectedJdkName: String) {
    val currentJdkName = projectJdkUtils.getGradleJdkNameFromIdeaGradleXmlFile()
    expect.that(currentJdkName).isEqualTo(expectedJdkName)
  }

  fun assertProjectJdk(expectedJdkName: String) {
    val currentJdkName = projectJdkUtils.getProjectJdkNameInIdeaXmlFile()
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