/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.fixtures.createDaemonJvmPropertiesFile
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleDaemonJvmSettingsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.TestApplicationManager
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@Suppress("UnstableApiUsage")
class UnsupportedDependencyJavaVersionIssueCheckerTest {
  private val issueChecker = UnsupportedDependencyJavaVersionIssueChecker()

  @get:Rule var tmpFolderRule = TemporaryFolder()

  @Before
  fun setUp() {
    TestApplicationManager.getInstance()
    tmpFolderRule.create()
  }

  @Test
  fun `test Given dependency requires newer JVM When build project Then expected build output exception is displayed`() {
    verifyBuildIssue(dependencyMinJdkVersion = "17", gradleJdkVersion = "11")
  }

  @Test
  fun `test Given dependency requires newer JVM When build project using Daemon toolchain Then expected build output exception is displayed`() {
    tmpFolderRule.root.createDaemonJvmPropertiesFile("11")
    verifyBuildIssue(dependencyMinJdkVersion = "17", gradleJdkVersion = "11", useDaemonJvmCriteria = true)
  }

  private fun verifyBuildIssue(
    dependencyMinJdkVersion: String,
    gradleJdkVersion: String,
    gradleVersion: String = "9.0",
    useDaemonJvmCriteria: Boolean = false,
  ) {
    val exception = createException(dependencyMinJdkVersion, gradleJdkVersion)
    val buildEnvironment =
      mock(BuildEnvironment::class.java).also { buildEnvironment ->
        val gradle = mock(GradleEnvironment::class.java).also { gradle -> doReturn(gradleVersion).whenever(gradle).gradleVersion }
        doReturn(gradle).whenever(buildEnvironment).gradle
      }
    val issueData = GradleIssueData(tmpFolderRule.root.absolutePath, exception, buildEnvironment, null)
    val issue = issueChecker.createBuildIssue(issueData)
    assertThat(issue).isNotNull()
    val expectedMessage =
      "This project is configured to use an older Gradle JVM that supports up to version $gradleJdkVersion but the " +
        "dependency 'com.example.dependency' requires a Gradle JVM that supports version $dependencyMinJdkVersion."
    assertThat(issue!!.description).contains(expectedMessage)
    val quickFixes = issue.quickFixes
    assertThat(quickFixes).hasSize(2)
    if (useDaemonJvmCriteria) {
      assertThat(quickFixes[0]).isInstanceOf(UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix::class.java)
      assertThat(quickFixes[1]).isInstanceOf(OpenGradleDaemonJvmSettingsQuickFix::class.java)
    } else {
      assertThat(quickFixes[0]).isInstanceOf(UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix::class.java)
      assertThat(quickFixes[1]).isInstanceOf(SelectJdkFromFileSystemQuickFix::class.java)
    }
  }

  private fun createException(dependencyMinJdkVersion: String, gradleJdkVersion: String): Throwable {
    val cause =
      RuntimeException(
        "Dependency requires at least JVM runtime version $dependencyMinJdkVersion. This build uses a Java $gradleJdkVersion JVM."
      )
    return object : RuntimeException("Could not resolve com.example.dependency.", cause) {
      override fun toString() = message!!
    }
  }
}
