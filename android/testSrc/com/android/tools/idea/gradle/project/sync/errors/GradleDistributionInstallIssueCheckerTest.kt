/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.TestDataProvider
import org.gradle.StartParameter
import org.gradle.wrapper.PathAssembler
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import java.io.IOException

class GradleDistributionInstallIssueCheckerTest : AndroidGradleTestCase() {
  private val gradleDistributionInstallIssueChecker = GradleDistributionInstallIssueChecker()

  fun testCheckIssue() {
    // Load the project just so that we can retrieve the gradle wrapper successfully.
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)

    val expectedError = "Could not install Gradle distribution from 'https://example.org/distribution.zip'."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(expectedError), null, null)
    val buildIssue = gradleDistributionInstallIssueChecker.check(issueData)
    val zipFile = getDistributionZipFile()

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(expectedError)
    if (zipFile != null) assertThat(buildIssue.description).contains(zipFile.path)
    // Verify QuickFix.
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(GradleDistributionInstallIssueChecker.DeleteFileAndSyncQuickFix::class.java)
  }

  private fun getDistributionZipFile(): File? {
    val wrapperConfiguration = GradleUtil.getWrapperConfiguration(projectFolderPath.path) ?: return null
    val pathAssembler = PathAssembler(StartParameter.DEFAULT_GRADLE_USER_HOME, projectFolderPath)
    val localDistribution = pathAssembler.getDistribution(wrapperConfiguration)
    var zip = localDistribution.zipFile
    try {
      zip = zip.canonicalFile
    }
    catch (e: IOException) { }
    return zip
  }

  fun testCheckIssueHandled() {
    assertThat(
      gradleDistributionInstallIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Could not install Gradle distribution from ",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}