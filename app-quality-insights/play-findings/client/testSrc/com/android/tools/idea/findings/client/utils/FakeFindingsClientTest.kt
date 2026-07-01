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
package com.android.tools.idea.findings.client.utils

import com.android.tools.idea.findings.client.FetchFindingsRequest
import com.android.tools.idea.findings.client.FindingsFilters
import com.android.tools.idea.findings.model.AffectedScope
import com.android.tools.idea.findings.model.FindingData
import com.android.tools.idea.findings.model.FindingSeverity
import com.android.tools.idea.findings.model.FindingType
import com.android.tools.idea.insights.LoadingState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Unit tests for [FakeFindingsClient].
 *
 * ### Why we need these tests:
 * 1. **Verify simulation accuracy**: Since [FakeFindingsClient] is widely used in other integration tests to mock the backend, we must
 *    guarantee that its local filtering logic (by package, severity, and type) exactly mirrors the expected contract of the real Play
 *    Developer API.
 * 2. **Regression prevention**: Ensures changes to the fake data store or filtering logic do not break downstream tests that rely on
 *    specific mock findings or structures.
 */
class FakeFindingsClientTest {

  private val client = FakeFindingsClient()

  @Test
  fun testComputeFindings_All_ExampleApp() {
    runBlocking {
      val result = client.fetchFindings(FetchFindingsRequest(FakeFindingsClient.TEST_PACKAGE_NAME))

      assertThat(result).isInstanceOf(LoadingState.Ready::class.java)
      val findings = (result as LoadingState.Ready).value
      assertThat(findings).hasSize(3) // 3 findings for com.example.app
      assertThat(findings.all { it.name.contains(FakeFindingsClient.TEST_PACKAGE_NAME) }).isTrue()
    }
  }

  @Test
  fun testComputeFindings_All_OtherApp() {
    runBlocking {
      val result = client.fetchFindings(FetchFindingsRequest(FakeFindingsClient.OTHER_PACKAGE_NAME))

      assertThat(result).isInstanceOf(LoadingState.Ready::class.java)
      val findings = (result as LoadingState.Ready).value
      assertThat(findings).hasSize(2) // 2 findings for com.other.app
      assertThat(findings.all { it.name.contains(FakeFindingsClient.OTHER_PACKAGE_NAME) }).isTrue()
    }
  }

  @Test
  fun testComputeFindings_FilterByType() {
    runBlocking {
      val request =
        FetchFindingsRequest(
          packageName = FakeFindingsClient.TEST_PACKAGE_NAME,
          filters = FindingsFilters(findingTypes = setOf(FindingType.DRM_APP_COMPAT)),
        )
      val result = client.fetchFindings(request)

      val findings = (result as LoadingState.Ready).value
      assertThat(findings).hasSize(3) // All 3 example.app findings are DRM_APP_COMPAT
      assertThat(findings.all { it.type == FindingType.DRM_APP_COMPAT }).isTrue()

      // Verify findings/1 details
      val drm1 = findings.find { it.name == "applications/${FakeFindingsClient.TEST_PACKAGE_NAME}/findings/1" }
      assertThat(drm1).isNotNull()
      assertThat(drm1!!.severity).isEqualTo(FindingSeverity.WARNING)
      assertThat(drm1.findingData).isInstanceOf(FindingData.DrmAppCompat::class.java)
      val drmData = drm1.findingData as FindingData.DrmAppCompat
      assertThat(drmData.drmPackageName).isEqualTo("com.bad.sdk")
      assertThat(drm1.affectedScopes).hasSize(1)
      val location = drm1.affectedScopes.first()
      assertThat(location).isInstanceOf(AffectedScope.Artifact::class.java)
      assertThat((location as AffectedScope.Artifact).versionCode).isEqualTo("101")

      // Verify findings/3 details
      val drm3 = findings.find { it.name == "applications/${FakeFindingsClient.TEST_PACKAGE_NAME}/findings/3" }
      assertThat(drm3).isNotNull()
      assertThat(drm3!!.affectedScopes).hasSize(1)
      val loc3 = drm3.affectedScopes.first()
      assertThat(loc3).isInstanceOf(AffectedScope.Release::class.java)
      assertThat((loc3 as AffectedScope.Release).releaseName).isEqualTo("release-v1.0")
    }
  }

  @Test
  fun testComputeFindings_FilterBySeverity() {
    runBlocking {
      val request =
        FetchFindingsRequest(
          packageName = FakeFindingsClient.TEST_PACKAGE_NAME,
          filters = FindingsFilters(severities = setOf(FindingSeverity.WARNING)),
        )
      val result = client.fetchFindings(request)

      val findings = (result as LoadingState.Ready).value
      assertThat(findings).hasSize(1) // Only findings/1 is WARNING for example.app (findings/4 is other.app)
      assertThat(findings.first().name).isEqualTo("applications/${FakeFindingsClient.TEST_PACKAGE_NAME}/findings/1")
    }
  }

  @Test
  fun testComputeFindings_FilterByTypeAndSeverity() {
    runBlocking {
      val request =
        FetchFindingsRequest(
          packageName = FakeFindingsClient.TEST_PACKAGE_NAME,
          filters = FindingsFilters(findingTypes = setOf(FindingType.DRM_APP_COMPAT), severities = setOf(FindingSeverity.SEVERE)),
        )
      val result = client.fetchFindings(request)

      val findings = (result as LoadingState.Ready).value
      assertThat(findings).hasSize(1)
      val finding = findings.first()
      assertThat(finding.name).isEqualTo("applications/${FakeFindingsClient.TEST_PACKAGE_NAME}/findings/2")
      assertThat(finding.severity).isEqualTo(FindingSeverity.SEVERE)
    }
  }

  @Test
  fun testComputeFindings_FilterNoMatch() {
    runBlocking {
      val request =
        FetchFindingsRequest(
          packageName = FakeFindingsClient.TEST_PACKAGE_NAME,
          filters = FindingsFilters(severities = setOf(FindingSeverity.BLOCKING)),
        )
      val result = client.fetchFindings(request)

      val findings = (result as LoadingState.Ready).value
      assertThat(findings).isEmpty()
    }
  }

  @Test
  fun testComputeFindings_FilterByUnknownType() {
    runBlocking {
      val request =
        FetchFindingsRequest(
          packageName = FakeFindingsClient.OTHER_PACKAGE_NAME, // findings/5 is in com.other.app
          filters = FindingsFilters(findingTypes = setOf(FindingType.UNKNOWN)),
        )
      val result = client.fetchFindings(request)

      val findings = (result as LoadingState.Ready).value
      assertThat(findings).hasSize(1)
      val finding = findings.first()
      assertThat(finding.name).isEqualTo("applications/${FakeFindingsClient.OTHER_PACKAGE_NAME}/findings/5")
      assertThat(finding.type).isEqualTo(FindingType.UNKNOWN)
      assertThat(finding.findingData).isInstanceOf(FindingData.Unknown::class.java)
      val unknownData = finding.findingData as FindingData.Unknown
      assertThat(unknownData.rawData).contains("rawKey")
    }
  }
}
