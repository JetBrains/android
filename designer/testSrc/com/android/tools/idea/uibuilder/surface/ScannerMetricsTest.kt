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

import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.analytics.CommonUsageTracker.Companion.NOP_TRACKER
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.google.wireless.android.sdk.stats.AtfAuditResult
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import java.util.function.Consumer

class ScannerMetricsTest : LayoutTestCase() {

  fun testLogEvent() {
    val metric = ScannerMetrics()
    metric.trigger = AtfAuditResult.Trigger.USER
    metric.scanMs = 10L
    metric.renderMs = 20L
    metric.errorCounts = 10
    metric.isRenderResultSuccess = false
    metric.componentCount = 5
    metric.counts.add(
      AtfAuditResult.AtfResultCount.newBuilder()
    )
    val usageTracker = object: CommonUsageTracker {
      override fun logAction(eventType: LayoutEditorEvent.LayoutEditorEventType) { }

      override fun logRenderResult(trigger: LayoutEditorRenderResult.Trigger?,
                                   result: RenderResult,
                                   wasInflated: Boolean) { }

      override fun logStudioEvent(eventType: LayoutEditorEvent.LayoutEditorEventType, consumer: Consumer<LayoutEditorEvent.Builder>?) {

        assertEquals(LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT, eventType)
        val builder = LayoutEditorEvent.newBuilder()
        consumer?.accept(builder)

        val result = builder.atfAuditResult

        assertEquals(AtfAuditResult.Trigger.USER, result.trigger)
        assertEquals(10L, result.auditDurationMs)
        assertEquals(20L, result.totalRenderTimeMs)
        assertEquals(10, result.errorCount)
        assertEquals(false, result.renderResult)
        assertEquals(5, result.componentCount)
        assertEquals(1, result.countsCount)
      }
    }

    metric.logEvent(usageTracker)
  }

  fun testClearAfterLogEvent() {
    val metric = ScannerMetrics()
    metric.trigger = AtfAuditResult.Trigger.USER
    metric.scanMs = 10L
    metric.renderMs = 20L
    metric.errorCounts = 10
    metric.isRenderResultSuccess = false
    metric.componentCount = 5
    metric.counts.add(
      AtfAuditResult.AtfResultCount.newBuilder()
    )
    val usageTracker = NOP_TRACKER

    metric.logEvent(usageTracker)

    assertEquals(AtfAuditResult.Trigger.UNKNOWN_TRIGGER, metric.trigger)
    assertEquals(0, metric.scanMs)
    assertEquals(0, metric.renderMs)
    assertEquals(0, metric.errorCounts)
    assertEquals(true, metric.isRenderResultSuccess)
    assertEquals(0, metric.componentCount)
    assertTrue(metric.counts.isEmpty())
  }
}