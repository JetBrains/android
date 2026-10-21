/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.findings.client

import com.android.tools.idea.findings.model.FindingSeverity
import com.android.tools.idea.findings.model.FindingType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for [FindingsFilters] serialization to AIP-160 filter strings. */
class FindingsFiltersTest {

  @Test
  fun testUnknownToProtoReturnsNull() {
    assertThat(FindingType.UNKNOWN.toProto()).isNull()
  }

  @Test
  fun testToFilterString_All() {
    val filters = FindingsFilters.ALL
    assertThat(filters.toFilterString()).isEmpty()
  }

  @Test
  fun testToFilterString_SingleType() {
    val filters = FindingsFilters(findingTypes = setOf(FindingType.DRM_APP_COMPAT))
    assertThat(filters.toFilterString()).isEqualTo("(finding_type = \"DRM_APP_COMPAT\")")
  }

  @Test
  fun testToFilterString_MultipleTypes_Sorted() {
    val filters = FindingsFilters(findingTypes = setOf(FindingType.DRM_APP_COMPAT, FindingType.UNKNOWN))
    assertThat(filters.toFilterString()).isEqualTo("(finding_type = \"DRM_APP_COMPAT\")")
  }

  @Test
  fun testToFilterString_OnlyUnknownType_ReturnsEmpty() {
    val filters = FindingsFilters(findingTypes = setOf(FindingType.UNKNOWN))
    assertThat(filters.toFilterString()).isEmpty()
  }

  @Test
  fun testToFilterString_SingleSeverity() {
    val filters = FindingsFilters(severities = setOf(FindingSeverity.WARNING))
    assertThat(filters.toFilterString()).isEqualTo("(finding_severity = \"WARNING\")")
  }

  @Test
  fun testToFilterString_MultipleSeverities_SortedAndJoinedWithOr() {
    val filters =
      FindingsFilters(severities = setOf(FindingSeverity.WARNING, FindingSeverity.INFO, FindingSeverity.BLOCKING, FindingSeverity.SEVERE))
    assertThat(filters.toFilterString())
      .isEqualTo(
        "(finding_severity = \"BLOCKING\" OR finding_severity = \"INFO\" OR finding_severity = \"SEVERE\" OR finding_severity = \"WARNING\")"
      )
  }

  @Test
  fun testToFilterString_CombinedTypeAndSeverity() {
    val filters =
      FindingsFilters(findingTypes = setOf(FindingType.DRM_APP_COMPAT), severities = setOf(FindingSeverity.WARNING, FindingSeverity.INFO))
    assertThat(filters.toFilterString())
      .isEqualTo("(finding_type = \"DRM_APP_COMPAT\") AND (finding_severity = \"INFO\" OR finding_severity = \"WARNING\")")
  }
}
