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
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.DataBindingTrackerSyncListener
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_LAMBDA
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_METHOD_REFERENCE
import com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_ACCEPTED
import com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_SUGGESTED
import com.intellij.facet.FacetManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DataBindingTrackerTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.NONE,
                         DataBindingMode.ANDROIDX)
  }

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  private val fixture: JavaCodeInsightTestFixture by lazy {
    projectRule.fixture as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())
    projectRule.fixture.copyDirectoryToProject(TestDataPaths.PROJECT_FOR_TRACKING, "src")

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!
    LayoutBindingModuleCache.getInstance(androidFacet).dataBindingMode = mode
  }

  @Test
  fun testDataBindingPollingMetadataTracking() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
      try {
        UsageTracker.setWriterForTest(tracker)
        DataBindingTrackerSyncListener(projectRule.project).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
        val dataBindingPollMetadata = tracker.usages
          .map { it.studioEvent }
          .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
          .map { it.dataBindingEvent.pollMetadata }
          .lastOrNull()

        if (mode == DataBindingMode.NONE) {
          assertThat(dataBindingPollMetadata).isNull()
        }
        else {
          dataBindingPollMetadata!!
          assertThat(dataBindingPollMetadata.dataBindingEnabled).isTrue()
          assertThat(dataBindingPollMetadata.layoutXmlCount).isEqualTo(4)
          assertThat(dataBindingPollMetadata.importCount).isEqualTo(0)
          assertThat(dataBindingPollMetadata.variableCount).isEqualTo(7)
          assertThat(dataBindingPollMetadata.moduleCount).isEqualTo(1)
          assertThat(dataBindingPollMetadata.dataBindingEnabledModuleCount).isEqualTo(1)
        }
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
  }

  @Test
  fun testTrackMethodReferenceCompletion() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    try {
      UsageTracker.setWriterForTest(tracker)

      fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public static void doSomethingStatic(View view) {}
      }
    """.trimIndent())

      val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{ModelWithBindableMethodsJava::d${caret}}"/>
      </layout>
    """.trimIndent())
      fixture.configureFromExistingVirtualFile(file.virtualFile)

      fixture.completeBasic()

      val completionSuggestedEvent = tracker.usages
        .map { it.studioEvent }
        .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
        .mapNotNull { it.dataBindingEvent }
        .lastOrNull { it.type == DATA_BINDING_COMPLETION_SUGGESTED }

      val completionAcceptedEvent = tracker.usages
        .map { it.studioEvent }
        .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
        .mapNotNull { it.dataBindingEvent }
        .lastOrNull { it.type == DATA_BINDING_COMPLETION_ACCEPTED }

      if (mode == DataBindingMode.NONE) {
        assertThat(completionSuggestedEvent).isNull()
        assertThat(completionAcceptedEvent).isNull()
      }
      else {
        assertThat(completionSuggestedEvent!!.context).isEqualTo(DATA_BINDING_CONTEXT_METHOD_REFERENCE)
        assertThat(completionAcceptedEvent!!.context).isEqualTo(DATA_BINDING_CONTEXT_METHOD_REFERENCE)
      }
    }
    finally {
      tracker.close()
      UsageTracker.cleanAfterTesting()
    }
  }

  @Test
  fun testTrackLambdaCompletion() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    try {
      UsageTracker.setWriterForTest(tracker)

      fixture.addClass("""
      package test.langdb;

      import android.view.View;

      public class ModelWithBindableMethodsJava {
        public void doSomething(View view) {}
      }
    """.trimIndent())

      val file = fixture.addFileToProject("res/layout/test_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="test.langdb.ModelWithBindableMethodsJava"/>
          <variable name="member" type="ModelWithBindableMethodsJava" />
        </data>
        <TextView
            android:id="@+id/c_0_0"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:gravity="center"
            android:onClick="@{() -> member.do${caret}}"/>
      </layout>
    """.trimIndent())
      fixture.configureFromExistingVirtualFile(file.virtualFile)

      fixture.completeBasic()

      val completionSuggestedEvent = tracker.usages
        .map { it.studioEvent }
        .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
        .mapNotNull { it.dataBindingEvent }
        .lastOrNull { it.type == DATA_BINDING_COMPLETION_SUGGESTED }

      val completionAcceptedEvent = tracker.usages
        .map { it.studioEvent }
        .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
        .mapNotNull { it.dataBindingEvent }
        .lastOrNull { it.type == DATA_BINDING_COMPLETION_ACCEPTED }

      if (mode == DataBindingMode.NONE) {
        assertThat(completionSuggestedEvent).isNull()
        assertThat(completionAcceptedEvent).isNull()
      }
      else {
        assertThat(completionSuggestedEvent!!.context).isEqualTo(DATA_BINDING_CONTEXT_LAMBDA)
        assertThat(completionAcceptedEvent!!.context).isEqualTo(DATA_BINDING_CONTEXT_LAMBDA)
      }
    }
    finally {
      tracker.close()
      UsageTracker.cleanAfterTesting()
    }
  }
}
