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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.FAKE_6_DAYS_AGO
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.FakeTimeProvider.now
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.client.Interval
import com.android.tools.idea.insights.client.QueryFilters
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val VERSION90 = Version(buildVersion = "90")
private val VERSION120 = Version(buildVersion = "120")
private val PIXEL_4A = Device(manufacturer = "Google", model = "Pixel 4a")
private val PIXEL_4 = Device(manufacturer = "Google", model = "Pixel 4")
private val ANDROID_12 = OperatingSystemInfo(displayVersion = "12", displayName = "Android (12)")
private val ANDROID_14 = OperatingSystemInfo(displayVersion = "14", displayName = "Android (14)")

class FilterBuilderTest {
  @Test
  fun `check all are selected case`() {
    val query =
      QueryFilters(
        interval = Interval(FAKE_6_DAYS_AGO, now),
        versions = setOf(Version.ALL),
        devices = setOf(Device.ALL),
        operatingSystems = setOf(OperatingSystemInfo.ALL),
        eventTypes = FailureType.values().toList(),
        signal = SignalType.SIGNAL_UNSPECIFIED,
        visibilityType = VisibilityType.ALL,
      )
    val generated = buildFiltersFromQuery(query)

    assertThat(generated).isEqualTo("(errorIssueType = ANR OR errorIssueType = CRASH)")
  }

  @Test
  fun `check some are selected case`() {
    val query =
      QueryFilters(
        interval = Interval(FAKE_6_DAYS_AGO, now),
        versions = setOf(VERSION120, VERSION90),
        devices = setOf(PIXEL_4A, PIXEL_4),
        operatingSystems = setOf(ANDROID_12, ANDROID_14),
        eventTypes = listOf(FailureType.FATAL, FailureType.ANR),
        signal = SignalType.SIGNAL_UNSPECIFIED,
        visibilityType = VisibilityType.USER_PERCEIVED,
      )

    val generated = buildFiltersFromQuery(query)
    assertThat(generated)
      .isEqualTo(
        "(apiLevel = 12 OR apiLevel = 14) " +
          "AND (deviceModel = Google/Pixel 4 OR deviceModel = Google/Pixel 4a) " +
          "AND (errorIssueType = ANR OR errorIssueType = CRASH) " +
          "AND (isUserPerceived) " +
          "AND (versionCode = 120 OR versionCode = 90)"
      )
  }

  @Test
  fun `check filtering by issue id case`() {
    val generated = FilterBuilder().apply { addIssue(IssueId("123")) }.build()

    assertThat(generated).isEqualTo("(issueId = 123)")
  }

  @Test
  fun `check filtering by error issue id case`() {
    val generated = FilterBuilder().apply { addErrorIssue(IssueId("123")) }.build()

    assertThat(generated).isEqualTo("(errorIssueId = 123)")
  }

  private fun buildFiltersFromQuery(queryFilters: QueryFilters): String {
    return FilterBuilder()
      .apply {
        addVersions(queryFilters.versions)
        addFailureTypes(queryFilters.eventTypes)
        addDevices(queryFilters.devices)
        addOperatingSystems(queryFilters.operatingSystems)
        addVisibilityType(queryFilters.visibilityType)
      }
      .build()
  }
}
