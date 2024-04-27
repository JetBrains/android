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
package com.android.tools.idea.insights.analytics

import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.components.service
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class AppInsightsPerformanceTrackerTest {
  private lateinit var usageTracker: TestUsageTracker

  @Before
  fun setUp() {
    // Set up test tracker
    usageTracker = TestUsageTracker(virtualExecutor)
    UsageTracker.setWriterForTest(usageTracker)
  }

  @After
  fun tearDown() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun `can log hourly`() {
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(10)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(3)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(1)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(2)

    // advance 1 hour and check logged contents
    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    checkLoggedContents(
      listOf(
        """
          type: PERFORMANCE_STATS
          performance_stats {
            vc_based_line_number_mapping_latency {
              min_latency_ms: 1
              p50_latency_ms: 2
              p90_latency_ms: 10
              max_latency_ms: 10
            }
          }
        """
          .trimIndent()
      )
    )

    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(20)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(7)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(5)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(6)

    // advance 1 hour and check new logged contents
    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    checkLoggedContents(
      listOf(
        """
          type: PERFORMANCE_STATS
          performance_stats {
            vc_based_line_number_mapping_latency {
              min_latency_ms: 1
              p50_latency_ms: 2
              p90_latency_ms: 10
              max_latency_ms: 10
            }
          }
        """
          .trimIndent(),
        """
          type: PERFORMANCE_STATS
          performance_stats {
            vc_based_line_number_mapping_latency {
              min_latency_ms: 5
              p50_latency_ms: 6
              p90_latency_ms: 20
              max_latency_ms: 20
            }
          }
        """
          .trimIndent()
      )
    )
  }

  @Test
  fun `log when there's new stats to report`() {
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(10)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(3)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(1)
    performanceTracker.recordVersionControlBasedLineNumberMappingLatency(2)

    // advance 1 hour and check logged contents
    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    checkLoggedContents(
      listOf(
        """
          type: PERFORMANCE_STATS
          performance_stats {
            vc_based_line_number_mapping_latency {
              min_latency_ms: 1
              p50_latency_ms: 2
              p90_latency_ms: 10
              max_latency_ms: 10
            }
          }
        """
          .trimIndent()
      )
    )

    // advance 1 hour and no new logged contents
    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    checkLoggedContents(
      listOf(
        """
          type: PERFORMANCE_STATS
          performance_stats {
            vc_based_line_number_mapping_latency {
              min_latency_ms: 1
              p50_latency_ms: 2
              p90_latency_ms: 10
              max_latency_ms: 10
            }
          }
        """
          .trimIndent()
      )
    )
  }

  @Test
  fun `do not log if nothing to report`() {
    // advance 1 hour and check logged contents
    virtualExecutor.advanceBy(1, TimeUnit.HOURS)
    checkLoggedContents(emptyList())
  }

  private fun getLoggedEvents(): List<AppQualityInsightsUsageEvent> {
    return usageTracker.usages
      .map { it.studioEvent }
      .filter { it.kind == AndroidStudioEvent.EventKind.APP_QUALITY_INSIGHTS_USAGE }
      .map { it.appQualityInsightsUsageEvent }
  }

  private fun checkLoggedContents(expected: List<String>) {
    val logged = getLoggedEvents()

    assertThat(logged.size).isEqualTo(expected.size)
    assertThat(logged.map { it.toString().trim() }).containsExactlyElementsIn(expected)
  }

  companion object {
    @JvmField @ClassRule val applicationRule = ApplicationRule()

    @JvmField @ClassRule val disposableRule = DisposableRule()

    private lateinit var performanceTracker: AppInsightsPerformanceTracker
    private lateinit var virtualExecutor: VirtualTimeScheduler

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      virtualExecutor = VirtualTimeScheduler()

      // Mock AppExecutorUtil.
      mockStatic<AppExecutorUtil>(disposableRule.disposable).apply {
        this.whenever<Any> {
            AppExecutorUtil.createBoundedScheduledExecutorService(
              "App Insights Performance Statistics Collector",
              1
            )
          }
          .thenReturn(virtualExecutor)
        this.whenever<Any> { AppExecutorUtil.getAppScheduledExecutorService() }.thenCallRealMethod()
      }

      performanceTracker = service()
    }
  }
}
