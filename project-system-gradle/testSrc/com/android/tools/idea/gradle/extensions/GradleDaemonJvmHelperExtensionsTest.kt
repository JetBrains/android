/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.extensions

import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper

class GradleDaemonJvmHelperExtensionsTest : LightPlatformTestCase() {

  fun testProjectNotUsingDaemonJvmCriteriaWithSupportedGradleVersion() {
    val gradleVersion = GradleVersion.version("8.10")

    createDaemonJvmPropertiesFile(null)
    createWrapperPropertiesFile(gradleVersion)

    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, project.basePath!!))
    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project.basePath!!, gradleVersion))
  }

  fun testProjectUsingDaemonJvmCriteriaWithUnsupportedGradleVersion() {
    val gradleVersion = GradleVersion.version("8.7")

    createDaemonJvmPropertiesFile("17")
    createWrapperPropertiesFile(gradleVersion)

    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, project.basePath!!))
    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project.basePath!!, gradleVersion))
  }

  fun testProjectUsingDaemonJvmCriteriaWithSupportedGradleVersion() {
    val gradleVersion = GradleVersion.version("8.10")

    createDaemonJvmPropertiesFile("string version")
    createWrapperPropertiesFile(gradleVersion)

    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, project.basePath!!))
    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project.basePath!!, gradleVersion))
  }

  private fun createDaemonJvmPropertiesFile(version: String?) {
    VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/gradle-daemon-jvm.properties", version?.let { "toolchainVersion=$version" }.orEmpty())
  }

  private fun createWrapperPropertiesFile(version: GradleVersion) {
    VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/wrapper/gradle-wrapper.properties", """
      distributionBase=PROJECT
      distributionPath=wrapper/dists
      distributionUrl=https\://services.gradle.org/distributions/gradle-${version.version}-bin.zip
      zipStoreBase=PROJECT
      zipStorePath=wrapper/dists
    """.trimIndent())
  }
}