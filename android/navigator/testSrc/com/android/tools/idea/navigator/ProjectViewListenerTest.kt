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
package com.android.tools.idea.navigator

import com.android.testutils.MockitoKt
import com.android.testutils.VirtualTimeDateProvider
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.FeatureSurveys
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProjectViewSelectionChangeEvent
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ProjectViewTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verifyNoInteractions
import java.util.concurrent.TimeUnit

@RunsInEdt
class ProjectViewListenerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val virtualTimeScheduler = VirtualTimeScheduler()
  private val virtualTimeProvider = VirtualTimeDateProvider(virtualTimeScheduler)
  private val tracker = TestUsageTracker(virtualTimeScheduler)

  @Before
  fun setup() {
    UsageTracker.setWriterForTest(tracker)
    ProjectViewTestUtil.setupImpl(projectRule.project, true)
  }

  @After
  fun cleanup() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testMetricsSentOnChange() {
    val listener = ProjectViewListener(projectRule.project, FeatureSurveys, virtualTimeScheduler, virtualTimeProvider)
    listener.toolWindowsRegistered(listOf(ToolWindowId.PROJECT_VIEW), ToolWindowManager.getInstance(projectRule.project))
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    ProjectView.getInstance(projectRule.project).changeView(ProjectViewPane.ID)
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    // Verify metrics sent
    Truth.assertThat(selectionChangeEvents()).containsExactly(
      ProjectViewSelectionChangeEvent.ProjectViewContent.UNKNOWN to ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID,
      ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID to ProjectViewSelectionChangeEvent.ProjectViewContent.PROJECT
    ).inOrder()
  }

  @Test
  fun testMetricsSentOnChangeImmediatelyAfterInit() {
    val listener = ProjectViewListener(projectRule.project, FeatureSurveys, virtualTimeScheduler, virtualTimeProvider)
    listener.toolWindowsRegistered(listOf(ToolWindowId.PROJECT_VIEW), ToolWindowManager.getInstance(projectRule.project))
    virtualTimeScheduler.advanceBy(1, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(ProjectViewPane.ID)
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    // Verify metrics sent
    Truth.assertThat(selectionChangeEvents()).containsExactly(
      ProjectViewSelectionChangeEvent.ProjectViewContent.UNKNOWN to ProjectViewSelectionChangeEvent.ProjectViewContent.PROJECT
    ).inOrder()
  }

  @Test
  fun testMetricsSentOnQuickChangeReturnBack() {
    val listener = ProjectViewListener(projectRule.project, FeatureSurveys, virtualTimeScheduler, virtualTimeProvider)
    listener.toolWindowsRegistered(listOf(ToolWindowId.PROJECT_VIEW), ToolWindowManager.getInstance(projectRule.project))
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(ProjectViewPane.ID)
    virtualTimeScheduler.advanceBy(1, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(ANDROID_VIEW_ID)
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    // Verify metrics sent
    Truth.assertThat(selectionChangeEvents()).containsExactly(
      ProjectViewSelectionChangeEvent.ProjectViewContent.UNKNOWN to ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID
    ).inOrder()
  }

  @Test
  fun testSentOnTwoQuickChangesToOtherViews() {
    val listener = ProjectViewListener(projectRule.project, FeatureSurveys, virtualTimeScheduler, virtualTimeProvider)
    listener.toolWindowsRegistered(listOf(ToolWindowId.PROJECT_VIEW), ToolWindowManager.getInstance(projectRule.project))
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(ProjectViewPane.ID)
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(ANDROID_VIEW_ID)
    virtualTimeScheduler.advanceBy(1, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(PackageViewPane.ID)
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    // Verify metrics sent
    Truth.assertThat(selectionChangeEvents()).containsExactly(
      ProjectViewSelectionChangeEvent.ProjectViewContent.UNKNOWN to ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID,
      ProjectViewSelectionChangeEvent.ProjectViewContent.ANDROID to ProjectViewSelectionChangeEvent.ProjectViewContent.PROJECT,
      ProjectViewSelectionChangeEvent.ProjectViewContent.PROJECT to ProjectViewSelectionChangeEvent.ProjectViewContent.OTHER
    ).inOrder()
  }

  @Test
  fun testSurveyTriggered() {
    val featureSurveysMock: FeatureSurveys = MockitoKt.mock()
    val listener = ProjectViewListener(projectRule.project, featureSurveysMock, virtualTimeScheduler, virtualTimeProvider)
    listener.toolWindowsRegistered(listOf(ToolWindowId.PROJECT_VIEW), ToolWindowManager.getInstance(projectRule.project))
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)
    ProjectView.getInstance(projectRule.project).changeView(ProjectViewPane.ID)
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    Mockito.verify(featureSurveysMock, times(1)).triggerSurveyByName("ANDROID_PROJECT_VIEW_UNSELECTED")
  }

  @Test
  fun testSurveyNotTriggeredWhenAndroidRemoved() {
    val featureSurveysMock: FeatureSurveys = MockitoKt.mock()
    val listener = ProjectViewListener(projectRule.project, featureSurveysMock, virtualTimeScheduler, virtualTimeProvider)
    listener.toolWindowsRegistered(listOf(ToolWindowId.PROJECT_VIEW), ToolWindowManager.getInstance(projectRule.project))
    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    ProjectView.getInstance(projectRule.project).apply {
      Truth.assertThat(currentViewId).isEqualTo(ANDROID_VIEW_ID)
      removeProjectPane(getProjectViewPaneById(ANDROID_VIEW_ID))
    }

    virtualTimeScheduler.advanceBy(11, TimeUnit.SECONDS)

    verifyNoInteractions(featureSurveysMock)
  }

  private fun selectionChangeEvents() = tracker.usages
    .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.PROJECT_VIEW_SELECTION_CHANGE_EVENT }
    .map { it.studioEvent.projectViewSelectionChangeEvent.run { this.viewBeforeChange to this.viewAfterChange } }

}