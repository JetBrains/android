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
package com.android.tools.idea.testartifacts.instrumented.testsuite.logging

import com.android.tools.analytics.UsageTracker
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionResultType
import javax.swing.JComponent
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

/**
 * Android Studio usage tracker for Android Test Suite feature.
 */
class AndroidTestSuiteLogger(private val usageLogReporter: UsageLogReporter = UsageLogReporterImpl,
                             private val timestamp: Long = System.currentTimeMillis()) {
  private val impressions: MutableSet<ParallelAndroidTestReportUiEvent.UiElement> = mutableSetOf()

  /**
   * Add impression event. The event is reported in bulk by [reportImpressions].
   */
  fun addImpression(element: ParallelAndroidTestReportUiEvent.UiElement) {
    impressions.add(element)
  }

  /**
   * Add impression events. The event is reported in bulk by [reportImpressions].
   */
  fun addImpressions(vararg elements: ParallelAndroidTestReportUiEvent.UiElement) {
    impressions.addAll(elements)
  }

  /**
   * Add impression event when a given component is displayed.
   * The event is reported in bulk by [reportImpressions].
   */
  fun addImpressionWhenDisplayed(component: JComponent,
                                 element: ParallelAndroidTestReportUiEvent.UiElement) {
    component.addAncestorListener(object: AncestorListener {
      // Note: This method is called when the source or one of its ancestors is made visible.
      override fun ancestorAdded(event: AncestorEvent) {
        impressions.add(element)
        component.removeAncestorListener(this)
      }
      override fun ancestorMoved(event: AncestorEvent) {}
      override fun ancestorRemoved(event: AncestorEvent) {}
    })
  }

  /**
   * Reports the impressions to the [UsageTracker] in bulk with the timestamp when
   * this logger is instantiated.
   */
  fun reportImpressions() {
    usageLogReporter.report(
      AndroidStudioEvent.newBuilder().apply {
        category = AndroidStudioEvent.EventCategory.TESTS
        kind = AndroidStudioEvent.EventKind.PARALLEL_ANDROID_TEST_REPORT_UI
        parallelAndroidTestReportUiEventBuilder.apply {
          addAllImpressions(impressions)
        }
      },
      timestamp)
    impressions.clear()
  }

  /**
   * Reports a click interaction immediately to the [UsageTracker].
   */
  fun reportClickInteraction(element: ParallelAndroidTestReportUiEvent.UiElement,
                             resultType: UserInteractionResultType = UserInteractionResultType.UNKNOWN_UI_INTERACTION_RESULT) {
    usageLogReporter.report(
      AndroidStudioEvent.newBuilder().apply {
        category = AndroidStudioEvent.EventCategory.TESTS
        kind = AndroidStudioEvent.EventKind.PARALLEL_ANDROID_TEST_REPORT_UI
        parallelAndroidTestReportUiEventBuilder.apply {
          addInteractionsBuilder().apply {
            type = ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionType.CLICK
            uiElement = element
            result = resultType
          }
        }
      }
    )
  }

  @VisibleForTesting
  fun getImpressionsForTesting(): Set<ParallelAndroidTestReportUiEvent.UiElement> = impressions
}

interface UsageLogReporter {
  fun report(studioEvent: AndroidStudioEvent.Builder, eventTimeMs: Long? = null)
}

private object UsageLogReporterImpl : UsageLogReporter {
  override fun report(studioEvent: AndroidStudioEvent.Builder, eventTimeMs: Long?) {
    if (eventTimeMs == null) {
      UsageTracker.log(studioEvent)
    } else {
      UsageTracker.log(eventTimeMs, studioEvent)
    }
  }
}