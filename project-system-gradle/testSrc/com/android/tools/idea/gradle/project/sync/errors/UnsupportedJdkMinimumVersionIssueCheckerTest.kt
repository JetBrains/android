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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import org.mockito.Mockito.spy

class UnsupportedJdkMinimumVersionIssueCheckerTest : AndroidGradleTestCase() {
  /** A UsageTracker implementation that allows introspection of logged metrics in tests. */
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  override fun setUp() {
    super.setUp()
    UsageTracker.setWriterForTest(usageTracker)
  }

  override fun tearDown() {
    super.tearDown()
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  fun testJdk8RequiredError() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val ideSdks = spy(IdeSdks.getInstance())
    whenever(ideSdks.isUsingJavaHomeJdk(project)).thenReturn(false)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, ideSdks, testRootDisposable)
    SimulatedSyncErrors.registerSyncErrorToSimulate(
      "com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0"
    )
    val message: String = requestSyncAndGetExpectedFailure()
    val expectedText = StringBuilder("com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0\n")
    expectedText.append("Please use JDK 8 or newer.\n")
    val androidStudio = IdeInfo.getInstance().isAndroidStudio

    // Verify hyperlinks are correct.
    if (androidStudio) { // Android Studio has extra quick-fix
      expectedText.append("<a href=\"use.java.home.as.jdk\">Set Android Studio to use the same JDK as Gradle and sync project</a>\n")
    }
    expectedText.append("<a href=\"select.jdk.from.gradle.settings\">Change Gradle JDK...</a>")
    assertThat(message).contains(expectedText.toString())
    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }
    assertEquals(GradleSyncFailure.JDK8_REQUIRED, event.studioEvent.gradleSyncFailure)
  }
}