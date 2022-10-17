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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.facet.FacetManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain


@RunsInEdt
class ViewBindingTrackerTest {
  private val projectRule =
    AndroidProjectRule.withAndroidModel(AndroidProjectBuilder(viewBindingOptions = { IdeViewBindingOptionsImpl(enabled = true) }))

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  @Before
  fun setUp() {
    assertThat(facet.isViewBindingEnabled()).isTrue()
    fixture.addFileToProject("src/main/AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())
  }

  @Test
  fun trackViewBindingPollingMetadata() {
    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent())

    val tracker = TestUsageTracker(VirtualTimeScheduler())
      try {
        UsageTracker.setWriterForTest(tracker)
        projectRule.project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
        val viewBindingPollMetadata = tracker.usages
          .map { it.studioEvent }
          .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
          .map { it.dataBindingEvent.viewBindingMetadata }
          .lastOrNull()

        assertThat(viewBindingPollMetadata!!.viewBindingEnabled).isTrue()
        assertThat(viewBindingPollMetadata.layoutXmlCount).isEqualTo(1)
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
  }

  @Test
  fun trackViewBindingProjectWithIgnoredLayouts() {
    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent())
    fixture.addFileToProject("src/main/res/layout/activity_ignored.xml", """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools" tools:viewBindingIgnore="true">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent())

    val tracker = TestUsageTracker(VirtualTimeScheduler())
      try {
        UsageTracker.setWriterForTest(tracker)
        projectRule.project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
        val viewBindingPollMetadata = tracker.usages
          .map { it.studioEvent }
          .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
          .map { it.dataBindingEvent.viewBindingMetadata }
          .lastOrNull()

        assertThat(viewBindingPollMetadata!!.layoutXmlCount).isEqualTo(1)
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
  }
}