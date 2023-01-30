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
package com.android.tools.idea.projectsystem.gradle

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.lint.detector.api.LintFix
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_OUTDATED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LINK_FOLLOWED
import org.jetbrains.android.AndroidTestCase

internal class IdeGooglePlaySdkIndexTest: AndroidTestCase() {
  fun testGenerateSdkLinkHasOnUrlOpenCallBack() {
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.initialize()
    val quickFix = ideIndex.generateSdkLinkLintFix("com.google.firebase", "firebase-auth", "9.0.0", null)
    assertThat(quickFix).isInstanceOf(LintFix.ShowUrl::class.java)
    assertWithMessage("onUrlOpen should be defined and ideally be used to report a $SDK_INDEX_LINK_FOLLOWED event")
      .that((quickFix as LintFix.ShowUrl).onUrlOpen).isNotNull()
  }

  fun testIssueIsBlockingReported() {
    val scheduler = VirtualTimeScheduler()
    val testUsageTracker = TestUsageTracker(scheduler)
    setWriterForTest(testUsageTracker)
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.initialize()
    ideIndex.isLibraryOutdated("com.google.firebase", "firebase-auth", "9.0.0", null)
    val loggedEvents = testUsageTracker.usages
    val libraryEvents = loggedEvents.filter { it.studioEvent.kind == SDK_INDEX_LIBRARY_IS_OUTDATED }
    assertThat(libraryEvents).hasSize(1)
    assertThat(libraryEvents[0].studioEvent.hasSdkIndexLibraryDetails()).isTrue()
    val libraryDetails = libraryEvents[0].studioEvent.sdkIndexLibraryDetails
    assertThat(libraryDetails.hasIsBlocking()).isTrue()
  }
}
