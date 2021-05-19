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

import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent.UiElement
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionResultType
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent.UserInteraction.UserInteractionType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.isNull
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [AndroidTestSuiteLogger].
 */
@RunWith(JUnit4::class)
class AndroidTestSuiteLoggerTest {

  @Mock lateinit var mockReporter: UsageLogReporter

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun impressionReporting() {
    val logger = AndroidTestSuiteLogger(usageLogReporter =  mockReporter, timestamp = 1234L)

    logger.addImpression(UiElement.TEST_SUITE_VIEW)
    logger.addImpressions(UiElement.TEST_SUITE_DEVICE_INFO_VIEW, UiElement.TEST_SUITE_LOG_VIEW)

    // Make sure duplicated items are not reported twice.
    logger.addImpressions(UiElement.TEST_SUITE_DEVICE_INFO_VIEW, UiElement.TEST_SUITE_LOG_VIEW)

    assertThat(logger.getImpressionsForTesting()).containsExactly(
      UiElement.TEST_SUITE_VIEW,
      UiElement.TEST_SUITE_DEVICE_INFO_VIEW,
      UiElement.TEST_SUITE_LOG_VIEW
    )

    logger.reportImpressions()

    val eventCaptor = ArgumentCaptor.forClass(AndroidStudioEvent.Builder::class.java)
    verify(mockReporter).report(eventCaptor.capture() ?: AndroidStudioEvent.newBuilder(),
                                eq(1234L))

    val event = eventCaptor.value
    assertThat(event.category).isEqualTo(EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(EventKind.PARALLEL_ANDROID_TEST_REPORT_UI)
    assertThat(event.parallelAndroidTestReportUiEvent.impressionsList).containsExactly(
      UiElement.TEST_SUITE_VIEW,
      UiElement.TEST_SUITE_DEVICE_INFO_VIEW,
      UiElement.TEST_SUITE_LOG_VIEW
    )

    // Once impressions are reported, the pending set should become empty.
    assertThat(logger.getImpressionsForTesting()).isEmpty()
  }

  @Test
  fun clickInteractionReporting() {
    val logger = AndroidTestSuiteLogger(usageLogReporter =  mockReporter, timestamp = 1234L)

    logger.reportClickInteraction(UiElement.TEST_SUITE_OPT_IN_BANNER, UserInteractionResultType.ACCEPT)

    val eventCaptor = ArgumentCaptor.forClass(AndroidStudioEvent.Builder::class.java)
    verify(mockReporter).report(eventCaptor.capture() ?: AndroidStudioEvent.newBuilder(),
                                isNull())

    val event = eventCaptor.value
    assertThat(event.category).isEqualTo(EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(EventKind.PARALLEL_ANDROID_TEST_REPORT_UI)
    assertThat(event.parallelAndroidTestReportUiEvent.interactionsCount).isEqualTo(1)
    assertThat(event.parallelAndroidTestReportUiEvent.interactionsList[0].type).isEqualTo(UserInteractionType.CLICK)
    assertThat(event.parallelAndroidTestReportUiEvent.interactionsList[0].uiElement).isEqualTo(UiElement.TEST_SUITE_OPT_IN_BANNER)
    assertThat(event.parallelAndroidTestReportUiEvent.interactionsList[0].result).isEqualTo(UserInteractionResultType.ACCEPT)
  }
}