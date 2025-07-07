/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.analytics

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.DataBindingTrackerSyncListener
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DataBindingDefaultTrackerTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath =
        resolveWorkspacePath("tools/adt/idea/android/testData/${TestProjectPaths.SIMPLE_APPLICATION}").toString()
    projectRule.fixture.copyDirectoryToProject("", "")
  }

  @Test
  fun testTrackDataBindingEnabled() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
      try {
        UsageTracker.setWriterForTest(tracker)
        DataBindingTrackerSyncListener(projectRule.project).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)

        assertThat(
          tracker.usages
            .map { it.studioEvent }
            .last { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
            .dataBindingEvent.pollMetadata.dataBindingEnabled)
          .isFalse()
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
    }
}
