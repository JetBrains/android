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

import com.android.ide.common.rendering.api.ViewInfo
import com.android.ide.common.rendering.api.Result
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderResultStats
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.AtfAuditResult
import com.intellij.lang.annotation.HighlightSeverity
import org.mockito.Mockito

class NlLayoutScannerMetricTrackerTest : LayoutTestCase() {

  companion object {
    private const val TOTAL_RENDER_TIME = 20L
    private const val ERROR_COUNT = 5
    private const val COMPONENT_COUNT = 2
    private const val IS_RENDER_SUCCESS = false

    fun createMetricTracker(): NlLayoutScannerMetricTracker {
      return NlLayoutScannerMetricTracker(mockNlDesignSurface())
    }

    private fun mockNlDesignSurface(): NlDesignSurface {
      val surface = Mockito.mock(NlDesignSurface::class.java)
      val sceneManager = Mockito.mock(LayoutlibSceneManager::class.java)
      Mockito.`when`(surface!!.sceneManager).thenReturn(sceneManager)

      val issueModel = Mockito.mock(IssueModel::class.java)
      Mockito.`when`(surface!!.issueModel).thenReturn(issueModel)
      Mockito.`when`(issueModel.issueCount).thenReturn(ERROR_COUNT)
      return surface
    }
  }

  fun testTrackTrigger() {
    val tracker = NlLayoutScannerMetricTracker(mockNlDesignSurface())
    tracker.trackTrigger(AtfAuditResult.Trigger.ISSUE_PANEL)

    assertEquals(AtfAuditResult.Trigger.ISSUE_PANEL, tracker.metric.trigger)
  }

  fun testTrackResult() {
    val tracker = NlLayoutScannerMetricTracker(mockNlDesignSurface())
    val mockResult = mockRenderResult()
    tracker.trackResult(mockResult, mockResult.validatorResult as ValidatorResult)

    assertEquals(AtfAuditResult.Trigger.UNKNOWN_TRIGGER, tracker.metric.trigger)
    assertEquals(TOTAL_RENDER_TIME, tracker.metric.renderMs)
    assertEquals(COMPONENT_COUNT, tracker.metric.componentCount)
    assertEquals(ERROR_COUNT, tracker.metric.errorCounts)
    assertEquals(IS_RENDER_SUCCESS, tracker.metric.isRenderResultSuccess)
  }

  fun testLogEvents() {
    val tracker = NlLayoutScannerMetricTracker(mockNlDesignSurface())
    val mockResult = mockRenderResult()
    tracker.trackResult(mockResult, mockResult.validatorResult as ValidatorResult)
    tracker.logEvents()

    // Logging must clear all metrics
    assertEquals(AtfAuditResult.Trigger.UNKNOWN_TRIGGER, tracker.metric.trigger)
    assertEquals(0L, tracker.metric.renderMs)
    assertEquals(0, tracker.metric.componentCount)
    assertEquals(0, tracker.metric.errorCounts)
    assertEquals(true, tracker.metric.isRenderResultSuccess)
  }

  fun testTrackIssue() {
    val countSize = 10
    val tracker = NlLayoutScannerMetricTracker(mockNlDesignSurface())

    for (i in 0 until countSize) {
      val issue: ValidatorData.Issue = buildIssue()
      tracker.trackIssue(issue)
    }

    assertEquals(countSize, tracker.metric.counts.size)
  }

  fun testIssueExpandedNotNlAtfIssue() {
    val tracker = NlLayoutScannerMetricTracker(mockNlDesignSurface())
    assertTrue(tracker.expandedTracker.opened.isEmpty())

    val issue = TestIssue()
    tracker.trackIssueExpanded(issue, true)

    assertTrue(tracker.expandedTracker.opened.isEmpty())
  }

  fun testIssueExpanded() {
    val tracker = NlLayoutScannerMetricTracker(mockNlDesignSurface())
    val issue = NlAtfIssue(buildIssue(), IssueSource.NONE)

    tracker.trackIssueExpanded(issue, true)

    assertEquals(1, tracker.expandedTracker.opened.size)
  }

  private fun buildIssue(): ValidatorData.Issue {
    return ValidatorData.Issue.IssueBuilder().setCategory("category")
      .setType(ValidatorData.Type.ACCESSIBILITY)
      .setMsg("Message")
      .setLevel(ValidatorData.Level.ERROR)
      .setSourceClass("SourceClass")
      .build()
  }

  private fun mockRenderResult(): RenderResult {
    val result = Mockito.mock(RenderResult::class.java)

    val validatorResult = ValidatorResult.Builder().build()
    Mockito.`when`(result.validatorResult).thenReturn(validatorResult)

    val builder = ImmutableList.Builder<ViewInfo>()
    for (i in 0 until COMPONENT_COUNT) {
      builder.add(Mockito.mock(ViewInfo::class.java))
    }
    Mockito.`when`(result.rootViews).thenReturn(builder.build())
    Mockito.`when`(result.stats).thenReturn(RenderResultStats(renderDurationMs = TOTAL_RENDER_TIME))

    val renderResult = Mockito.mock(Result::class.java)
    Mockito.`when`(result.renderResult).thenReturn(renderResult)
    Mockito.`when`(renderResult.isSuccess).thenReturn(IS_RENDER_SUCCESS)

    return result
  }

  private class TestIssue : Issue() {
    override val summary: String
      get() = ""
    override val description: String
      get() = ""
    override val severity: HighlightSeverity
      get() = HighlightSeverity.ERROR
    override val source: IssueSource
      get() = IssueSource.NONE
    override val category: String
      get() = ""
  }
}