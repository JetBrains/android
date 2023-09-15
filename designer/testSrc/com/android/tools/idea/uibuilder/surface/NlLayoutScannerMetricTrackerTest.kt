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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants
import com.android.tools.idea.common.analytics.CommonNopTracker
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.validator.ValidatorData
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class NlLayoutScannerMetricTrackerTest : LayoutTestCase() {

  @Mock lateinit var mockSurface: NlDesignSurface
  @Mock lateinit var mockModel: NlModel

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)
    (CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker).resetLastTrackedEvent()
  }

  @Test
  fun trackIgnoreButtonClicked() {
    val tracker = NlLayoutScannerMetricTracker(mockSurface)
    val issue = ScannerTestHelper.createTestIssueBuilder().build()
    tracker.trackIgnoreButtonClicked(issue)

    val usageTracker = CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker
    assertEquals(
      LayoutEditorEvent.LayoutEditorEventType.IGNORE_ATF_RESULT,
      usageTracker.lastTrackedEvent
    )
  }

  @Test
  fun trackApplyFixButtonClicked() {
    val tracker = NlLayoutScannerMetricTracker(mockSurface)
    val fixDescription = "Set this item's android:textColor to #FFFFFF"
    val viewAttribute =
      ValidatorData.ViewAttribute(SdkConstants.ANDROID_URI, "android", "textColor")
    val setAttributeFix =
      ValidatorData.SetViewAttributeFix(viewAttribute, "#FFFFFF", fixDescription)
    val issue = ScannerTestHelper.createTestIssueBuilder(setAttributeFix).build()
    tracker.trackApplyFixButtonClicked(issue)

    val usageTracker = CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker
    assertEquals(
      LayoutEditorEvent.LayoutEditorEventType.APPLY_ATF_FIX,
      usageTracker.lastTrackedEvent
    )
  }

  @Test
  fun clear() {
    val tracker = NlLayoutScannerMetricTracker(mockSurface)
    val issue = createTestNlAtfIssue()
    tracker.expanded.add(issue)

    tracker.clear()
    assertEmpty(tracker.expanded)
  }

  private fun createTestNlAtfIssue(): NlAtfIssue {
    val issue: ValidatorData.Issue = ScannerTestHelper.createTestIssueBuilder().build()
    return NlAtfIssue(issue, IssueSource.NONE, mockModel)
  }
}
