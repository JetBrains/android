/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.errors.ApplyGradlePluginQuickFix
import com.android.tools.idea.gradle.project.sync.errors.GetGradleSettingsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import org.junit.Test

class GradleDslMethodNotFoundIssueCheckerTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun testCheckIssueWithMethodNotFoundInSettingsFile() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val settingsFile = preparedProject.root.resolve(SdkConstants.FN_SETTINGS_GRADLE)
    settingsFile.writeText("incude ':app'")

    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        assertThat(buildIssue.title).isEqualTo("Gradle Sync issues.")
        assertThat(buildIssue.description).startsWith("Gradle DSL method not found: 'incude()'")
        // Verify quickFixes.
        assertThat(buildIssue.quickFixes).hasSize(1)
        val quickFix = buildIssue.quickFixes[0]
        assertThat(quickFix).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
        assertThat((quickFix as OpenFileAtLocationQuickFix).myFilePosition.file.path).isEqualTo(settingsFile.path)
      },
      AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND
    )
  }

  @Test
  fun testCheckIssueWithMethodNotFoundInBuildFile() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nabdd()")

    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        assertThat(buildIssue.title).isEqualTo("Gradle Sync issues.")
        assertThat(buildIssue.description).startsWith("Gradle DSL method not found: 'abdd()'")
        assertThat(buildIssue.description).contains("Your project may be using a version of the Android Gradle plug-in that does not contain the " +
                                                    "method (e.g. 'testCompile' was added in 1.1.0).")
        // Verify quickFixes.
        assertThat(buildIssue.quickFixes).hasSize(3)
        assertThat(buildIssue.quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionQuickFix::class.java)
        assertThat(buildIssue.quickFixes[1]).isInstanceOf(GetGradleSettingsQuickFix::class.java)
        assertThat(buildIssue.quickFixes[2]).isInstanceOf(ApplyGradlePluginQuickFix::class.java)
      },
      AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND
    )
  }
}