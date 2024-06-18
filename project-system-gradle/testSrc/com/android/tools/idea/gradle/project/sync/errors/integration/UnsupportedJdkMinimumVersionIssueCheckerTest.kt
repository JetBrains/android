/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.sdk.IdeSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import org.junit.Test

class UnsupportedJdkMinimumVersionIssueCheckerTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun testJdk8RequiredError() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val mockedIdeSdks = object : IdeSdks() {
      override fun isUsingJavaHomeJdk(project: Project): Boolean {
        return false
      }
    }
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockedIdeSdks, projectRule.testRootDisposable)

    SimulatedSyncErrors.registerSyncErrorToSimulate(
      "com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0"
    )

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      verifyBuildIssue = { buildIssue ->
        expect.that(buildIssue).isNotNull()
        val expectedText = StringBuilder("com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0\n")
        expectedText.append("Please use JDK 8 or newer.\n")
        val androidStudio = IdeInfo.getInstance().isAndroidStudio

        // Verify hyperlinks are correct.
        if (androidStudio) { // Android Studio has extra quick-fix
          expectedText.append("<a href=\"use.java.home.as.jdk\">Set Android Studio to use the same JDK as Gradle and sync project</a>\n")
        }
        expectedText.append("<a href=\"select.jdk.from.gradle.settings\">Change Gradle JDK...</a>")
        expect.that(buildIssue.description).isEqualTo(expectedText.toString())
        expect.that(buildIssue.quickFixes).hasSize(2)
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.JDK8_REQUIRED,
      expectedPhasesReported = null // Because of using simulated error phases are not relevant in this test
    )
  }
}